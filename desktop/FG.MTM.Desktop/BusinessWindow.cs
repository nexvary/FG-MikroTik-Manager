using System.Data;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using FG.MTM.Desktop.Server;

namespace FG.MTM.Desktop;
public partial class MainWindow
{
    private List<BusinessRecord> business = [];
    private long revision;
    private int businessPage;
    private const int PageSize = 50;
    private static readonly (string Key, string En, string Ar)[] Tables = [
        ("subscribers","Subscribers","المشتركون"),("plans","Plans","الباقات"),("invoices","Invoices","الفواتير"),
        ("ledger","Ledger","السجل المالي"),("sales","Sales","المبيعات"),("expenses","Expenses","المصروفات"),
        ("team_members","Team / resellers","الموظفون والموزعون"),("reseller_entries","Reseller entries","حركة الموزعين"),
        ("router_bindings","Router bindings","ارتباطات الراوتر"),("network_jobs","Network jobs","مهام الشبكة"),
        ("payment_details","Payment details","تفاصيل الدفع"),("invoice_voids","Invoice voids","إلغاء الفواتير"),
        ("sale_voids","Sale voids","إلغاء المبيعات"),("import_batches","Import batches","دفعات الاستيراد"),
        ("audit","Audit","سجل التدقيق"),("organizations","Organizations","المؤسسة"),("branches","Branches","الفروع")];
    private void LocalizeBusiness()
    {
        var selected = BusinessTables.SelectedValue as string ?? "subscribers";
        BusinessTables.DisplayMemberPath = "Value"; BusinessTables.SelectedValuePath = "Key";
        BusinessTables.ItemsSource = Tables.Select(t => new KeyValuePair<string,string>(t.Key,T(t.En,t.Ar))).ToList();
        BusinessTables.SelectedValue = selected;
        BusinessRefresh.Content = T("Read from server","قراءة من الخادم"); BusinessExport.Content = T("Export matching CSV","تصدير النتائج CSV");
        BusinessPrevious.Content = T("Previous","السابق"); BusinessNext.Content = T("Next","التالي");
        BusinessSearch.ToolTip = T("Search loaded records","بحث في السجلات المحمّلة");
        BusinessNotice.Text = T("Read-only synchronized branch data. Financial edits remain in Android. Amounts ending in _minor are integer minor currency units (100 = 1.00). Search then export all matching loaded records.","بيانات الفرع المتزامنة للعرض. التعديلات المالية من Android. القيم المنتهية بـ _minor بوحدات العملة الصغرى الصحيحة (100 = 1.00). التصدير يشمل كل النتائج المطابقة المحمّلة.");
        DiagnosticRun.Content = T("Run server diagnostics","بدء تشخيص الخادم");
        DiagnosticNotice.Text = T("These tests run on FG Server: database, DNS, TCP and verified TLS. They do not measure your PC or MikroTik LAN. A target timeout does not prove a complete Internet outage.","الفحوص من خادم FG: قاعدة البيانات وDNS وTCP وشهادة TLS. لا تقيس شبكة الكمبيوتر أو MikroTik المحلية. انتهاء مهلة هدف لا يثبت انقطاع الإنترنت بالكامل.");
        DrawBusiness();
    }
    private IEnumerable<BusinessRecord> MatchingBusiness() => business.Where(r => r.Table == (BusinessTables.SelectedValue as string ?? "subscribers") && (r.Id + " " + r.Body.GetRawText()).Contains(BusinessSearch.Text.Trim(),StringComparison.OrdinalIgnoreCase));
    private void DrawBusiness()
    {
        if (!ready) return;
        var records = MatchingBusiness().ToList();
        businessPage = Math.Clamp(businessPage,0,Math.Max(0,(records.Count-1)/PageSize));
        var fields = records.SelectMany(r => r.Body.ValueKind == JsonValueKind.Object ? r.Body.EnumerateObject().Select(p => p.Name) : []).Where(k => k != "id").Distinct().Order().ToList();
        var table = new DataTable(); table.Columns.Add("id"); foreach (var field in fields) table.Columns.Add(field);
        foreach (var record in records.Skip(businessPage*PageSize).Take(PageSize)) {
            var row = table.NewRow(); row["id"] = record.Id;
            foreach (var field in fields) if (record.Body.TryGetProperty(field,out var value)) row[field] = value.ValueKind == JsonValueKind.String ? value.GetString() : value.GetRawText();
            table.Rows.Add(row);
        }
        BusinessGrid.ItemsSource = table.DefaultView;
        BusinessCount.Text = T($"Revision {revision} · {records.Count} matches · page {businessPage+1}/{Math.Max(1,(records.Count+PageSize-1)/PageSize)}",$"إصدار {revision} · {records.Count} نتيجة · صفحة {businessPage+1}/{Math.Max(1,(records.Count+PageSize-1)/PageSize)}");
        BusinessPrevious.IsEnabled = businessPage>0; BusinessNext.IsEnabled = (businessPage+1)*PageSize<records.Count;
    }
    private async Task LoadBusiness()
    {
        business = []; DrawBusiness();
        var data = await client.GetBusinessAsync(lifetime.Token);
        business = data.Records!; revision = data.Revision; businessPage=0; DrawBusiness(); UpdateSummary();
    }
    private async void RefreshBusiness(object sender,RoutedEventArgs e) { if (!busy) await Run(LoadBusiness); }
    private void BusinessFilterChanged(object sender,SelectionChangedEventArgs e) { if (ready) { businessPage=0; DrawBusiness(); } }
    private void BusinessSearchChanged(object sender,TextChangedEventArgs e) { if (ready) { businessPage=0; DrawBusiness(); } }
    private void PreviousBusiness(object sender,RoutedEventArgs e) { businessPage--; DrawBusiness(); }
    private void NextBusiness(object sender,RoutedEventArgs e) { businessPage++; DrawBusiness(); }
    private void ExportBusiness(object sender,RoutedEventArgs e)
    {
        if (!client.IsAuthenticated) { ClearResults(); Status.Text=T("Log in again","سجّل الدخول مرة أخرى"); return; }
        var rows = MatchingBusiness().ToList();
        var dialog = new Microsoft.Win32.SaveFileDialog { Filter="CSV (*.csv)|*.csv", FileName=$"FG-MTM-{BusinessTables.SelectedValue}.csv" };
        if (dialog.ShowDialog(this) != true) return;
        try { System.IO.File.WriteAllText(dialog.FileName,BusinessCsv.Write(rows),new UTF8Encoding(true)); Status.Text=T("CSV saved","تم حفظ CSV"); }
        catch (Exception ex) when (ex is System.IO.IOException or UnauthorizedAccessException) { Status.Text=T("Unable to save file","تعذر حفظ الملف"); }
    }
    private async void RunDiagnostics(object sender,RoutedEventArgs e)
    {
        if (busy) return;
        await Run(LoadDiagnostics);
    }
    private async Task LoadDiagnostics()
    {
            DiagnosticGrid.ItemsSource = null;
            var report = await client.DiagnoseAsync(lifetime.Token);
            var table = new DataTable(); foreach (var label in new[]{T("Check","الفحص"),T("Target","الهدف"),T("Result","النتيجة"),T("Time (ms)","الوقت (مللي ثانية)")}) table.Columns.Add(label);
            foreach (var check in report.Checks!) table.Rows.Add(check.Key,check.Target,check.State switch {"passed"=>T("Passed","نجح"),"failed"=>T("Failed","فشل"),_=>T("Not measured","لم يُقَس")},check.ElapsedMs?.ToString("0.##")??"—");
            DiagnosticGrid.ItemsSource=table.DefaultView;
            Status.Text = T("Server check: ","فحص الخادم: ")+report.CheckedAt+(report.Cached?T(" · cached"," · نتيجة محفوظة"):"");

    }
    private void UpdateSummary() { if (ready) DashboardSummary.Text = client.IsAuthenticated ? T($"FG Server connected · {users.Count} RADIUS users · {business.Count} business records · revision {revision}",$"متصل بخادم FG · {users.Count} مستخدم RADIUS · {business.Count} سجل أعمال · إصدار {revision}") : T("FG Machines · connect to your server","FG Machines · اتصل بخادمك"); }
    private object NavigationLabel(string key)
    {
        var paths = new Dictionary<string,string> {
            ["Dashboard"]="M3,3 H10 V10 H3 Z M14,3 H21 V10 H14 Z M3,14 H10 V21 H3 Z M14,14 H21 V21 H14 Z",
            ["Business"]="M5,3 H19 V21 H5 Z M8,7 H16 M8,11 H16 M8,15 H16",
            ["Subscribers"]="M12,3 A4,4 0 1 1 12,11 A4,4 0 1 1 12,3 M4,21 C4,11 20,11 20,21",
            ["Diagnostics"]="M3,12 H7 L10,5 L14,19 L17,12 H21",
            ["Settings"]="M3,6 H21 M3,12 H21 M3,18 H21 M8,3 V9 M16,9 V15 M10,15 V21" };
        var panel = new StackPanel {Orientation=Orientation.Horizontal};
        panel.Children.Add(new System.Windows.Shapes.Path {Data=Geometry.Parse(paths.GetValueOrDefault(key,"M3,5 H21 V19 H3 Z M7,9 H17 M7,14 H13")),Stroke=new SolidColorBrush((Color)ColorConverter.ConvertFromString(key=="Diagnostics"?"#B0FF35":"#ECD178")),StrokeThickness=1.6,Width=20,Height=20,Stretch=Stretch.Uniform,Margin=new Thickness(0,0,10,0)});
        panel.Children.Add(new TextBlock {Text=Label(key),VerticalAlignment=VerticalAlignment.Center}); return panel;
    }
}
