using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using FG.MTM.Desktop.Server;
using FG.MTM.Desktop.Router;
using System.Data;
namespace FG.MTM.Desktop;
public partial class MainWindow : Window
{
    private readonly HttpClient http = new(Environment.GetCommandLineArgs().Contains("--smoke-test") ? (HttpMessageHandler)new SmokeHandler() : new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = TimeSpan.FromSeconds(25), MaxResponseContentBufferSize = 4 * 1024 * 1024 };
    private readonly FgServerClient client;
    private readonly RouterRestClient router;
    private readonly CancellationTokenSource lifetime = new();
    private readonly Stack<string> history = new();
    private string page = "Dashboard";
    private bool arabic = true, ready, busy;
    private IReadOnlyList<RadiusUser> users = [];
    private IReadOnlyList<RadiusSession> sessions = [];
    private static readonly (string Key, string Ar)[] Pages = [("Dashboard","الرئيسية"),("Routers","الراوترات"),("Subscribers","المشتركون"),("HotSpot","هوت سبوت"),("PPPoE","PPPoE"),("RADIUS","RADIUS"),("Business","الأعمال"),("Employees","الموظفون"),("Diagnostics","التشخيص"),("Advanced","الإعداد المتقدم"),("Settings","الإعدادات")];
    public MainWindow()
    {
        client = new(http); router = new(http);
        InitializeComponent(); ready = true; Localize(); Navigate("Dashboard", false);
        var timer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(5) };
        timer.Tick += (_, _) => { if (!busy && client.Identity is not null && !client.IsAuthenticated) { client.Logout(); ClearResults(); Status.Text = T("Session expired. Log in again.", "انتهت الجلسة. سجل الدخول مرة أخرى."); } };
        timer.Start();
        if (Environment.GetCommandLineArgs().Contains("--smoke-test")) Loaded += async (_, _) => {
            try {
                Title = "FG MTM — UI acceptance fixture";
                client.SetBaseAddress("https://fixture.example");
                await client.LoginAsync("fixture", "main", "test", "test password");
                await LoadRadius(); await LoadBusiness();
                BusinessSearch.Text = "Customer 51"; DrawBusiness();
                if (BusinessGrid.Items.Count != 1) throw new InvalidOperationException("Business search failed");
                BusinessSearch.Text = ""; DrawBusiness();
                if (BusinessGrid.Items.Count != 50) throw new InvalidOperationException("Business page failed");
                NextBusiness(this, new RoutedEventArgs());
                if (BusinessGrid.Items.Count != 1) throw new InvalidOperationException("Business next failed");
                PreviousBusiness(this, new RoutedEventArgs());
                foreach (bool rtl in new[] { false, true }) {
                    arabic = rtl; Localize();
                    if (FlowDirection != (rtl ? FlowDirection.RightToLeft : FlowDirection.LeftToRight)) throw new InvalidOperationException("Direction mismatch");
                    AdvancedMode.IsChecked = true;
                    foreach (var item in Pages) {
                        Navigate(item.Key);
                        if (item.Key == "Diagnostics") {
                            await LoadDiagnostics();
                            if (DiagnosticGrid.Items.Count != 3) throw new InvalidOperationException("Diagnostic view failed");
                        }
                        if (PageTitle.Text != Label(item.Key)) throw new InvalidOperationException("Navigation mismatch");
                        if (item.Key is "Dashboard" or "Business" or "Settings" or "RADIUS" or "Diagnostics" or "Advanced") {
                            UpdateLayout();
                            var bitmap = new System.Windows.Media.Imaging.RenderTargetBitmap((int)ActualWidth, (int)ActualHeight, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
                            bitmap.Render(this);
                            var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder();
                            encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap));
                            System.IO.Directory.CreateDirectory("desktop-proof");
                            using var file = System.IO.File.Create($"desktop-proof/{item.Key}-{(rtl ? "ar" : "en")}.png");
                            encoder.Save(file);
                        }
                    }
                    var previous = history.Peek(); GoBack(this, new RoutedEventArgs()); if (page != previous) throw new InvalidOperationException("Back mismatch");
                    if (Navigation.Children.Count != Pages.Length) throw new InvalidOperationException("Missing navigation");
                }
                await client.LogoutAsync(); ClearResults();
                if (BusinessGrid.Items.Count != 0 || client.IsAuthenticated) throw new InvalidOperationException("Logout retained data");
                Application.Current.Shutdown(0);
            } catch { Application.Current.Shutdown(1); }
        };
        Closed += (_, _) => { timer.Stop(); };
        Closed += (_, _) => { lifetime.Cancel(); client.Logout(); router.Dispose(); http.Dispose(); lifetime.Dispose(); };
    }
    private string T(string en, string ar) => arabic ? ar : en;
    private string Label(string key) => arabic ? Pages.First(p => p.Key == key).Ar : key;
    private void BuildNavigation()
    {
        Navigation.Children.Clear();
        foreach (var item in Pages.Where(p => p.Key != "Advanced" || AdvancedMode.IsChecked == true))
        {
            var button = new Button { Content = NavigationLabel(item.Key), HorizontalContentAlignment = arabic ? HorizontalAlignment.Right : HorizontalAlignment.Left, Tag = item.Key };
            button.Click += (_, _) => Navigate((string)button.Tag); Navigation.Children.Add(button);
        }
    }
    private void Navigate(string key, bool remember = true)
    {
        if (remember && key != page) history.Push(page);
        page = key; PageTitle.Text = Label(key); BackButton.IsEnabled = history.Count > 0;
        SettingsPanel.Visibility = key == "Settings" ? Visibility.Visible : Visibility.Collapsed;
        BusinessPanel.Visibility = key is "Business" or "Employees" or "Subscribers" ? Visibility.Visible : Visibility.Collapsed;
        DiagnosticPanel.Visibility = key == "Diagnostics" ? Visibility.Visible : Visibility.Collapsed;
        RadiusPanel.Visibility = key == "RADIUS" ? Visibility.Visible : Visibility.Collapsed;
        RouterPanel.Visibility = key is "Routers" or "HotSpot" or "PPPoE" or "Advanced" ? Visibility.Visible : Visibility.Collapsed;
        ConfigureRouterModules();
        OverviewPanel.Visibility = SettingsPanel.Visibility == Visibility.Collapsed && RadiusPanel.Visibility == Visibility.Collapsed && RouterPanel.Visibility == Visibility.Collapsed && BusinessPanel.Visibility == Visibility.Collapsed && DiagnosticPanel.Visibility == Visibility.Collapsed ? Visibility.Visible : Visibility.Collapsed;
        OverviewText.Text = key == "Dashboard" ? T("Read synchronized business data, RADIUS sessions and server diagnostics. Financial edits and voucher creation remain in Android.","تابع بيانات الأعمال المتزامنة وجلسات RADIUS وتشخيص الخادم. تعديل الأموال وإنشاء الكروت يتم من تطبيق Android.") : T("This Windows module is not available yet. No live operation has been performed.","القسم ده لسه غير متاح في ويندوز. لم تُنفّذ أي عملية على الشبكة.");
        ScopeText.Text = client.Identity is { } id ? $"{id.Tenant} / {id.Branch} — {id.Role}" : T("Not connected", "غير متصل");
        if (key == "Subscribers") BusinessTables.SelectedValue = "subscribers";
        if (key == "Employees") BusinessTables.SelectedValue = "team_members";
        DrawBusiness(); UpdateSummary(); ApplyFilters();
    }
    private void GoBack(object sender, RoutedEventArgs e) { if (history.Count > 0) Navigate(history.Pop(), false); }
    private void OpenSettings(object sender, RoutedEventArgs e) => Navigate("Settings");
    private void ToggleLanguage(object sender, RoutedEventArgs e) { arabic = !arabic; Localize(); Navigate(page, false); }
    private void ModeChanged(object sender, RoutedEventArgs e) { if (ready) { BuildNavigation(); if (page == "Advanced" && AdvancedMode.IsChecked != true) Navigate("Dashboard"); } }
    private void Localize()
    {
        FlowDirection = arabic ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        LanguageButton.Content = arabic ? "English" : "العربية";
        AdvancedMode.Content = T("Advanced mode","الوضع المتقدم"); BackButton.Content = T("← Back","رجوع →");
        ConnectShortcut.Content = T("Connect to FG Server","الاتصال بخادم FG");
        UrlLabel.Text = T("FG Server HTTPS address","عنوان خادم FG عبر HTTPS"); TenantLabel.Text = T("Tenant","الشركة"); BranchLabel.Text = T("Branch","الفرع"); UsernameLabel.Text = T("Username","اسم المستخدم"); PasswordLabel.Text = T("Password","كلمة المرور");
        LoginButton.Content = T("Login","دخول"); LogoutButton.Content = T("Logout","خروج"); RefreshButton.Content = T("Refresh","تحديث");
        ActiveOnly.Content = T("Active only","النشط فقط"); ExpiredOnly.Content = T("Expired","منتهي"); DisabledOnly.Content = T("Disabled","معطّل"); SearchLabel.Text = T("Search username / NAS / session ID","ابحث بالاسم أو NAS أو معرّف الجلسة"); UsersLabel.Text = T("Users","المستخدمون"); SessionsLabel.Text = T("Sessions","الجلسات");
        RadiusNotice.Text = T("Results belong only to the authenticated tenant and branch. To change branch, log in to its account. Enable/disable, renewal, top-up, profiles and disconnect are unavailable in the current API.","النتائج تخص الشركة والفرع المسجلين فقط. لتغيير الفرع سجل بحسابه. التفعيل والتعطيل والتجديد والشحن وتغيير الباقة وفصل الجلسة غير متاحة في API الحالي.");
        string[] userHeaders = arabic ? ["اسم المستخدم","مفعّل","الانتهاء","جلسات نشطة","الثواني","رفع (بايت)","تنزيل (بايت)","الإجمالي (بايت)"] : ["Username","Enabled","Expires","Active sessions","Seconds","Upload bytes","Download bytes","Total bytes"];
        string[] sessionHeaders = arabic ? ["اسم المستخدم","NAS","معرّف الجلسة","متوقفة","الثواني","رفع (بايت)","تنزيل (بايت)","الإجمالي (بايت)"] : ["Username","NAS","Session ID","Stopped","Seconds","Upload bytes","Download bytes","Total bytes"];
        for (int i = 0; i < 8; i++) { UsersGrid.Columns[i].Header = userHeaders[i]; SessionsGrid.Columns[i].Header = sessionHeaders[i]; }
        RouterNotice.Text = T("RouterOS 7 REST requires the router's HTTPS service and a trusted certificate. Views are read-only. Phone/PC reachability does not prove Internet access; physical AP links are unknown. RouterOS 6 API support and write tools are pending.","يلزم RouterOS 7 وخدمة HTTPS وشهادة موثوقة. العرض للقراءة فقط. وصول الكمبيوتر للراوتر لا يثبت اتصال الإنترنت؛ روابط أجهزة الشبكة غير معروفة. دعم API لـ RouterOS 6 وأدوات التعديل قيد التنفيذ.");
        RouterUrlLabel.Text = T("Router HTTPS address","عنوان الراوتر عبر HTTPS"); RouterUserLabel.Text = T("Router username","اسم مستخدم الراوتر"); RouterPasswordLabel.Text = T("Router password","كلمة مرور الراوتر");
        RouterConnectButton.Content = T("Connect","اتصال"); RouterDisconnectButton.Content = T("Disconnect","قطع الاتصال"); RouterRefreshButton.Content = T("Refresh","تحديث");
        LocalizeBusiness(); BuildNavigation();
    }
    private async void Login(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        await Run(async () => {
            client.SetBaseAddress(ServerUrl.Text.Trim());
            var password = Password.Password; Password.Clear();
            await client.LoginAsync(Tenant.Text.Trim(), Branch.Text.Trim(), Username.Text.Trim(), password, lifetime.Token);
            password = "";
            await LoadRadius();
            if (client.Identity?.Role == "owner") await LoadBusiness();
            Navigate("Dashboard");
        });
    }
    private async void Logout(object sender, RoutedEventArgs e) { if (busy) return; await Run(async () => { try { await client.LogoutAsync(lifetime.Token); } finally { ClearResults(); Password.Clear(); Navigate("Settings"); } Status.Text = T("Signed out", "تم تسجيل الخروج"); }); }
    private async void Refresh(object sender, RoutedEventArgs e) { if (!busy) await Run(LoadRadius); }
    private async Task LoadRadius()
    {
        // Publish both results together; never display data left over from an expired session.
        ClearResults();
        var newUsers = await client.GetRadiusUsersAsync(lifetime.Token);
        var newSessions = await client.GetRadiusSessionsAsync(false, lifetime.Token);
        users = newUsers; sessions = newSessions; ApplyFilters();
        Status.Text = T("Updated from FG Server","تم التحديث من خادم FG");
    }
    private async Task Run(Func<Task> action)
    {
        busy = true; BusinessRefresh.IsEnabled = BusinessExport.IsEnabled = DiagnosticRun.IsEnabled = false; LoginButton.IsEnabled = LogoutButton.IsEnabled = RefreshButton.IsEnabled = RouterConnectButton.IsEnabled = RouterDisconnectButton.IsEnabled = RouterRefreshButton.IsEnabled = false;
        Status.Text = T("Loading…","جارٍ التحميل…");
        try { await action(); }
        catch (OperationCanceledException) { if (!lifetime.IsCancellationRequested) Status.Text = T("Request timed out. Retry manually.","انتهت مهلة الطلب. أعد المحاولة."); }
        catch (UnauthorizedAccessException) { ClearResults(); RouterGrid.ItemsSource = null; RouterState.Text = T("Access denied or disconnected","الوصول مرفوض أو الاتصال مقطوع"); Status.Text = T("Login required, access denied or account scope mismatch.","سجّل الدخول: الجلسة انتهت أو الوصول مرفوض أو الشركة والفرع مختلفان."); }
        catch (ArgumentException) { Status.Text = T("Check the HTTPS origin, tenant, branch and username.","راجع عنوان HTTPS والشركة والفرع واسم المستخدم."); }
        catch (Exception ex) when (ex is HttpRequestException or System.Text.Json.JsonException or InvalidOperationException)
        { ClearResults(); Status.Text = T("Server unavailable or invalid response. Check the address and retry.","الخادم غير متاح أو الرد غير صالح. راجع العنوان وأعد المحاولة."); }
        finally { busy = false; BusinessRefresh.IsEnabled = BusinessExport.IsEnabled = DiagnosticRun.IsEnabled = true; LoginButton.IsEnabled = LogoutButton.IsEnabled = RefreshButton.IsEnabled = RouterConnectButton.IsEnabled = RouterDisconnectButton.IsEnabled = RouterRefreshButton.IsEnabled = true; ScopeText.Text = client.Identity is { } id ? $"{id.Tenant} / {id.Branch} — {id.Role}" : T("Not connected","غير متصل"); }
    }

    private void ConfigureRouterModules()
    {
        if (!ready) return;
        var selected = RouterModules.SelectedValue as string;
        var choices = page switch {
            "HotSpot" => new[] { "HotSpot users", "HotSpot online" },
            "PPPoE" => new[] { "PPPoE users", "PPPoE online" },
            "Advanced" => RouterRestClient.Modules.Keys.ToArray(),
            "Diagnostics" => new[] { "Health", "Interfaces", "Routes", "DNS", "Logs", "Neighbors" },
            _ => new[] { "Health", "Interfaces", "Neighbors" }
        };
        string[] ar = ["حالة الراوتر","المنافذ","الجسر","VLAN","عناوين IP","DHCP","DNS","المسارات","الجدار الناري","NAT","Mangle","السرعات","واي فاي","مستخدمو هوت سبوت","متصلو هوت سبوت","مستخدمو PPPoE","متصلو PPPoE","WireGuard","الملفات","السجلات","السكربتات","المهام المجدولة","الأجهزة المجاورة"];
        var labels = RouterRestClient.Modules.Keys.Select((key, i) => new KeyValuePair<string,string>(key, arabic ? ar[i] : key)).Where(p => choices.Contains(p.Key)).ToList();
        RouterModules.DisplayMemberPath = "Value"; RouterModules.SelectedValuePath = "Key";
        RouterModules.ItemsSource = labels;
        RouterModules.SelectedValue = choices.Contains(selected) ? selected : choices[0];
        RouterGrid.ItemsSource = null;
    }
    private async void ConnectRouter(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        await Run(async () => {
            RouterGrid.ItemsSource = null;
            var password = RouterPassword.Password; RouterPassword.Clear();
            await router.ConnectAsync(RouterUrl.Text.Trim(), RouterUsername.Text.Trim(), password, lifetime.Token);
            password = ""; await ReadRouter();
        });
    }
    private void DisconnectRouter(object sender, RoutedEventArgs e)
    {
        if (busy) return; router.Disconnect(); RouterPassword.Clear(); RouterGrid.ItemsSource = null;
        RouterState.Text = T("Disconnected","تم قطع الاتصال");
    }
    private async void RefreshRouter(object sender, RoutedEventArgs e) { if (!busy) await Run(ReadRouter); }
    private void RouterModuleChanged(object sender, SelectionChangedEventArgs e) { if (ready) { RouterGrid.ItemsSource = null; RouterState.Text = T("Press Refresh to read the selected module.","اضغط تحديث لقراءة القسم المختار."); } }
    private async Task ReadRouter()
    {
        RouterGrid.ItemsSource = null;
        var module = RouterModules.SelectedValue as string ?? "Health";
        var rows = await router.ReadAsync(module, lifetime.Token);
        var table = new DataTable();
        foreach (var field in rows.SelectMany(r => r.Keys).Distinct()) table.Columns.Add(field);
        foreach (var row in rows) { var item = table.NewRow(); foreach (var field in row) item[field.Key] = field.Value; table.Rows.Add(item); }
        RouterGrid.ItemsSource = table.DefaultView;
        RouterState.Text = T($"Router reachable. {rows.Count} records. Internet and AP connectivity unverified.",$"الراوتر متاح. {rows.Count} سجل. اتصال الإنترنت والأكسس غير متحقق منه.");
    }

    private void ClearResults() { users = []; sessions = []; business = []; revision = 0; DiagnosticGrid.ItemsSource = null; DrawBusiness(); UpdateSummary(); ApplyFilters(); }
    private void FilterChanged(object sender, RoutedEventArgs e) { if (ready) ApplyFilters(); }
    private void SearchChanged(object sender, TextChangedEventArgs e) { if (ready) ApplyFilters(); }
    private void ApplyFilters()
    {
        if (!ready) return;
        var q = Search.Text.Trim();
        bool Matches(string value) => value.Contains(q, StringComparison.OrdinalIgnoreCase);
        var filtered = users.Where(u => Matches(u.Username) && (ActiveOnly.IsChecked != true || u.ActiveSessions > 0) && (ExpiredOnly.IsChecked != true || u.IsExpired) && (DisabledOnly.IsChecked != true || !u.Enabled)).ToList();
        UsersGrid.ItemsSource = filtered;
        var names = filtered.Select(u => u.Username).ToHashSet(StringComparer.Ordinal);
        SessionsGrid.ItemsSource = sessions.Where(s => (Matches(s.Username) || Matches(s.Nas) || Matches(s.Session)) && (ActiveOnly.IsChecked != true || !s.Stopped) && ((ExpiredOnly.IsChecked != true && DisabledOnly.IsChecked != true) || names.Contains(s.Username))).ToList();
        if (page is "RADIUS" or "Subscribers" && !busy) Status.Text = !client.IsAuthenticated ? T("Login to load live data.","سجّل الدخول لعرض البيانات الفعلية.") : T($"{filtered.Count} users",$"{filtered.Count} مستخدم");
    }
}
