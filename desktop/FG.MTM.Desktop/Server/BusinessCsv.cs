using System.Text.Json;
namespace FG.MTM.Desktop.Server;
public static class BusinessCsv
{
    public static string Cell(string value)
    {
        var trimmed = value.TrimStart();
        if (trimmed.Length>0 && "=+-@".Contains(trimmed[0]) || value.StartsWith('\t') || value.StartsWith('\r')) value="'"+value;
        return "\""+value.Replace("\"","\"\"")+"\"";
    }
    public static string Write(IReadOnlyList<BusinessRecord> rows)
    {
        var fields = rows.SelectMany(r=>r.Body.EnumerateObject().Select(p=>p.Name)).Where(k=>k!="id").Distinct().Order().ToList();
        var lines = new List<string>{string.Join(",",new[]{"id"}.Concat(fields).Select(Cell))};
        foreach(var row in rows) lines.Add(string.Join(",",new[]{Cell(row.Id)}.Concat(fields.Select(k=> row.Body.TryGetProperty(k,out var v)?Cell(v.ValueKind==JsonValueKind.String?v.GetString()??"":v.GetRawText()):Cell("")))));
        return string.Join("\r\n",lines)+"\r\n";
    }
}
