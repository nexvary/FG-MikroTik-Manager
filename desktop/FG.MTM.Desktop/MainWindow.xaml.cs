using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using FG.MTM.Desktop.Server;
namespace FG.MTM.Desktop;
public partial class MainWindow : Window
{
    private readonly HttpClient http = new(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = TimeSpan.FromSeconds(25), MaxResponseContentBufferSize = 4 * 1024 * 1024 };
    private readonly FgServerClient client;
    private readonly CancellationTokenSource lifetime = new();
    private readonly Stack<string> history = new();
    private string page = "Dashboard";
    private bool arabic, ready, busy;
    private IReadOnlyList<RadiusUser> users = [];
    private IReadOnlyList<RadiusSession> sessions = [];
    private static readonly (string Key, string Ar)[] Pages = [("Dashboard","الرئيسية"),("Routers","الراوترات"),("Subscribers","المشتركون"),("HotSpot","هوت سبوت"),("PPPoE","PPPoE"),("RADIUS","RADIUS"),("Vouchers","الكروت"),("Portal Studio","صفحة الدخول"),("Business","الأعمال"),("Employees","الموظفون"),("Diagnostics","التشخيص"),("Advanced","الإعداد المتقدم"),("Settings","الإعدادات")];
    public MainWindow()
    {
        client = new(http);
        InitializeComponent(); ready = true; Localize(); Navigate("Dashboard", false);
        var timer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(5) };
        timer.Tick += (_, _) => { if (!busy && client.Identity is not null && !client.IsAuthenticated) { client.Logout(); ClearResults(); Status.Text = T("Session expired. Log in again.", "انتهت الجلسة. سجل الدخول مرة أخرى."); } };
        timer.Start();
        if (Environment.GetCommandLineArgs().Contains("--smoke-test")) Loaded += (_, _) => {
            try {
                foreach (bool rtl in new[] { false, true }) {
                    arabic = rtl; Localize();
                    if (FlowDirection != (rtl ? FlowDirection.RightToLeft : FlowDirection.LeftToRight)) throw new InvalidOperationException("Direction mismatch");
                    AdvancedMode.IsChecked = true;
                    foreach (var item in Pages) { Navigate(item.Key); if (PageTitle.Text != Label(item.Key)) throw new InvalidOperationException("Navigation mismatch"); }
                    var previous = history.Peek(); GoBack(this, new RoutedEventArgs()); if (page != previous) throw new InvalidOperationException("Back mismatch");
                    if (Navigation.Children.Count != Pages.Length) throw new InvalidOperationException("Missing navigation");
                }
                Application.Current.Shutdown(0);
            } catch { Application.Current.Shutdown(1); }
        };
        Closed += (_, _) => { timer.Stop(); }; 
        Closed += (_, _) => { lifetime.Cancel(); client.Logout(); http.Dispose(); lifetime.Dispose(); };
    }
    private string T(string en, string ar) => arabic ? ar : en;
    private string Label(string key) => arabic ? Pages.First(p => p.Key == key).Ar : key;
    private void BuildNavigation()
    {
        Navigation.Children.Clear();
        foreach (var item in Pages.Where(p => p.Key != "Advanced" || AdvancedMode.IsChecked == true))
        {
            var button = new Button { Content = Label(item.Key), HorizontalContentAlignment = arabic ? HorizontalAlignment.Right : HorizontalAlignment.Left, Tag = item.Key };
            button.Click += (_, _) => Navigate((string)button.Tag); Navigation.Children.Add(button);
        }
    }
    private void Navigate(string key, bool remember = true)
    {
        if (remember && key != page) history.Push(page);
        page = key; PageTitle.Text = Label(key); BackButton.IsEnabled = history.Count > 0;
        SettingsPanel.Visibility = key == "Settings" ? Visibility.Visible : Visibility.Collapsed;
        RadiusPanel.Visibility = key is "RADIUS" or "Subscribers" ? Visibility.Visible : Visibility.Collapsed;
        OverviewPanel.Visibility = SettingsPanel.Visibility == Visibility.Collapsed && RadiusPanel.Visibility == Visibility.Collapsed ? Visibility.Visible : Visibility.Collapsed;
        OverviewText.Text = key == "Dashboard" ? T("Connect to FG Server to inspect RADIUS users and sessions. Router and business modules are still being implemented.","اتصل بخادم FG لعرض مستخدمي RADIUS والجلسات. أقسام إدارة الراوتر والأعمال ما زالت قيد التنفيذ.") : T("This Windows module is not available yet. No live operation has been performed.","القسم ده لسه غير متاح في ويندوز. لم تُنفّذ أي عملية على الشبكة.");
        ScopeText.Text = client.Identity is { } id ? $"{id.Tenant} / {id.Branch} — {id.Role}" : T("Not connected", "غير متصل");
        ApplyFilters();
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
        BuildNavigation();
    }
    private async void Login(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        await Run(async () => {
            client.SetBaseAddress(ServerUrl.Text.Trim());
            var password = Password.Password; Password.Clear();
            await client.LoginAsync(Tenant.Text.Trim(), Branch.Text.Trim(), Username.Text.Trim(), password, lifetime.Token);
            password = "";
            Navigate("RADIUS"); await LoadRadius();
        });
    }
    private void Logout(object sender, RoutedEventArgs e) { if (busy) return; client.Logout(); ClearResults(); Password.Clear(); Status.Text = T("Logged out. The server session expires within 15 minutes.","تم الخروج. تنتهي جلسة الخادم خلال 15 دقيقة."); Navigate(page, false); }
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
        busy = true; LoginButton.IsEnabled = LogoutButton.IsEnabled = RefreshButton.IsEnabled = false;
        Status.Text = T("Loading…","جارٍ التحميل…");
        try { await action(); }
        catch (OperationCanceledException) { if (!lifetime.IsCancellationRequested) Status.Text = T("Request timed out. Retry manually.","انتهت مهلة الطلب. أعد المحاولة."); }
        catch (UnauthorizedAccessException) { ClearResults(); Status.Text = T("Login required, access denied or account scope mismatch.","سجّل الدخول: الجلسة انتهت أو الوصول مرفوض أو الشركة والفرع مختلفان."); }
        catch (ArgumentException) { Status.Text = T("Check the HTTPS origin, tenant, branch and username.","راجع عنوان HTTPS والشركة والفرع واسم المستخدم."); }
        catch (Exception ex) when (ex is HttpRequestException or System.Text.Json.JsonException or InvalidOperationException)
        { ClearResults(); Status.Text = T("Server unavailable or invalid response. Check the address and retry.","الخادم غير متاح أو الرد غير صالح. راجع العنوان وأعد المحاولة."); }
        finally { busy = false; LoginButton.IsEnabled = LogoutButton.IsEnabled = RefreshButton.IsEnabled = true; ScopeText.Text = client.Identity is { } id ? $"{id.Tenant} / {id.Branch} — {id.Role}" : T("Not connected","غير متصل"); }
    }
    private void ClearResults() { users = []; sessions = []; ApplyFilters(); }
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
