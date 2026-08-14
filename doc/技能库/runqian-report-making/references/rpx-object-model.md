# RPX Official Object Model

Use this reference when reading or modifying a binary `.rpx` template through Runqian's installed Java libraries.

## Local Configuration

Read `../config/local.json` before compiling or launching anything. Treat it as the persistent contract for the current machine and validate it with `../scripts/validate-local-environment.ps1`. Generate it with `../scripts/detect-local-environment.ps1` only when absent or proven stale. Treat the values below as examples from one installation, not portable defaults.

Use `../scripts/invoke-rpx-tool.ps1` for routine inspection and supported writes. Its persistent Java source lives under the skill, and its compiled classes are cached under LocalAppData. Do not create task-specific Java source or class files in a business project.

## Runtime Setup

Discover the installed paths first. One known Windows installation uses:

```text
D:\soft\raqsoft\report\lib\*
D:\soft\raqsoft\report\web\webapps\demo\WEB-INF\lib\*
D:\soft\raqsoft\report\web\webapps\demo\WEB-INF\classes
D:\soft\raqsoft\report\web\tomcat\lib\*
D:\soft\raqsoft\common\jdbc\*
```

Load the installed license before reading a report:

```java
import com.raqsoft.report.model.engine.ExtCellSet;
import com.raqsoft.report.util.ReportUtils;

ExtCellSet.readLicense(
    "D:\\soft\\raqsoft\\report\\web\\webapps\\demo\\WEB-INF\\classes\\defaultlicensetrial_zh.xml");
IReport report = ReportUtils.read(path);
```

Run Java with `-Dstart.home=D:\soft\raqsoft\report` when the runtime requires the installation root.

## Structural Inspection

Prefer the persistent wrapper for normal work:

```powershell
& '<skill>/scripts/invoke-rpx-tool.ps1' -Action Inspect -Path '<absolute-rpx-path>'
```

It returns logical rows and columns, row/column dimensions, cell values and expressions, merge regions, fonts, alignment, wrapping, borders, metadata counts, and stable metadata hashes.

Use the API details below when extending the persistent tool for a reusable capability that it does not yet support.

Use logical API counts:

```java
report.getRowCount();
report.getColCount();
report.getRowCell(row).getRowHeight();
report.getColCell(col).getColWidth();
```

Inspect a cell safely:

```java
INormalCell cell = report.getCell(row, col);
if (cell != null) {
    Object literal = cell.getValue();
    Object expression = cell.getExpMap() == null
        ? null
        : cell.getExpMap().get(INormalCell.VALUE);
    Area merge = cell.getMergedArea();
}
```

Column visibility and the designer's “隐藏列” setting are independent:

```java
boolean columnVisible = report.getColCell(col).getColVisible();
boolean hiddenByCell = cell.getColHidden();
```

`columnVisible == true` does not override `hiddenByCell == true`. Inspect `getColHidden()` on every logical cell that must print, including subordinate merged cells and otherwise empty cells. Clear an accidental designer checkbox with `cell.setColHidden(false)` and verify that no other serialized cell metadata changed.

Merged subordinate cells can be `null` after serialization. Never assume every coordinate returns a cell.

Inspect metadata:

```java
ParamMetaData params = report.getParamMetaData();
DataSetMetaData datasets = report.getDataSetMetaData();
PrintSetup print = report.getPrintSetup();
```

For SQL datasets, inspect the SQL, datasource name, parameter count, parameter expressions, and parameter types.

## Safe Mutation Pattern

Prefer a guarded JSON patch through `invoke-rpx-tool.ps1`. The wrapper accepts JSON from memory, so no patch document is required on disk. The tool serializes and re-reads the modified report in memory, verifies all untouched content, then overwrites the exact target and performs a second readback.

Guard the expected target before changing it:

```java
if (report.getRowCount() != expectedRows || report.getColCount() != expectedCols) {
    throw new IllegalStateException("Unexpected RPX structure");
}
```

Set an expression through the expression map:

```java
cell.getExpMap(true).put(INormalCell.VALUE, expression);
```

Write back to the same path only after all mutations succeed:

```java
ReportUtils.write(path, report);
```

Immediately re-read the file and verify it. Do not rely on successful `write()` alone.

## Rebuilding Merged Headers

When merge geometry changes, replace affected cells instead of only clearing merge flags:

```java
for (int row = firstRow; row <= lastRow; row++) {
    for (int col = 1; col <= report.getColCount(); col++) {
        INormalCell fresh = new NormalCell();
        // Apply base font, alignment, wrap, borders, and colors here.
        report.setCell(row, col, fresh);
    }
}
```

Then apply one `Area` to every coordinate in the merged range:

```java
Area area = new Area(row1, col1, row2, col2);
for (int row = row1; row <= row2; row++) {
    for (int col = col1; col <= col2; col++) {
        INormalCell cell = report.getCell(row, col);
        cell.setMergedArea(area);
        cell.setValue(value);
    }
}
```

Apply title font and alignment to every cell in its merged area. This prevents subordinate cells from retaining stale styles.

Do not replace detail cells when their expression maps must survive.

## Detail Expansion And Master Cells

A dataset field expression alone does not necessarily expand into all rows. Use one sequence-producing cell as the vertical master, then bind the remaining cells in that detail row to it.

```java
INormalCell master = report.getCell(detailRow, 1);
master.getExpMap(true).put(INormalCell.VALUE, "ds_rows.select(YHMC)");

for (int col = 2; col <= report.getColCount(); col++) {
    INormalCell detail = report.getCell(detailRow, col);
    detail.setLeftHead("A" + detailRow);
}
```

Keep each dependent cell's own field expression, such as `ds_rows.GJJE`. The left master controls row synchronization so every field follows the same dataset record. Use `setTopHead(...)` for horizontal expansion relationships. After writing, read back `getLeftHead()`, `getTopHead()`, the master expression, and every dependent expression; a template that renders only the first record usually has a scalar master expression or missing master-cell relationships.

## Common Cell Properties

```java
cell.setFontName("宋体");
cell.setFontSize((short) 8);
cell.setBold(false);
cell.setHAlign(INormalCell.HALIGN_CENTER);
cell.setVAlign(INormalCell.VALIGN_MIDDLE);
cell.setTextWrap(true);
cell.setAdjustSizeMode(INormalCell.ADJUST_FIXED_WRAP);
```

Adjust modes:

```text
ADJUST_EXTEND      48
ADJUST_FIXED       49
ADJUST_FILL        50
ADJUST_SHRINK      51
ADJUST_FIXED_WRAP  52
```

Use `ADJUST_FIXED_WRAP` for fixed-size wrapping and `ADJUST_SHRINK` for single-line shrink-to-fit.

For values already stored as percentage points, append a literal percent sign without scaling:

```java
cell.setFormat("0.00'%'"); // 19.42 -> 19.42%
```

Do not use `0.00%` for those values; Java-compatible numeric formatting treats it as a ratio and displays `19.42` as `1942.00%`. Keep percentage units in the corresponding column headers when no global percentage unit applies.

For dense fixed-width reports, let percentage headers wrap but keep percentage detail values on one line:

```java
header.setTextWrap(true);
header.setAdjustSizeMode(INormalCell.ADJUST_FIXED_WRAP);

detail.setTextWrap(false);
detail.setAdjustSizeMode(INormalCell.ADJUST_SHRINK);
```

This keeps `122.21%` together while allowing labels such as `较年初增减（%）` to use multiple header lines.

## Borders And Background

Set all four borders explicitly when rebuilding a table cell:

```java
cell.setLBStyle(INormalCell.LINE_SOLID);
cell.setRBStyle(INormalCell.LINE_SOLID);
cell.setTBStyle(INormalCell.LINE_SOLID);
cell.setBBStyle(INormalCell.LINE_SOLID);
cell.setLBWidth(0.5f);
cell.setRBWidth(0.5f);
cell.setTBWidth(0.5f);
cell.setBBWidth(0.5f);
```

Use `NormalCell.COLOR_WHITE` and `NormalCell.COLOR_BLACK` for standard white background and black text/borders. Do not add shading unless it belongs to the accepted prototype.

## Dynamic Date Expressions

Useful installed Runqian functions include:

```text
date(string)
string(date, "yyyy年M月d日")
pdate@me(date)       month end
left(string, n)
right(string, n)
number(string)
```

Example end-month date:

```text
string(pdate@me(date(ds_header.select(TJJSNY)+"-01")),"yyyy年M月d日")
```

Build dynamic labels from confirmed dataset fields rather than adding SQL solely for display text.

## Print Setup

Typical A4 landscape setup:

```java
PrintSetup print = report.getPrintSetup();
print.setPaper(PrintSetup.A4_PAPERSIZE);
print.setOrientation(PrintSetup.LANDSCAPE);
print.setLeftMargin(8f);
print.setRightMargin(8f);
print.setTopMargin(10f);
print.setBottomMargin(10f);
print.setZoomMode(PrintSetup.ZOOM_WIDTH);
print.setHAlign(PrintSetup.HALIGN_CENTER);
```

Do not blindly overwrite existing print settings. Compare them with the accepted prototype and page-width calculation first.

## Designer Lifecycle

Before writing:

```powershell
$process = Get-Process report -ErrorAction Stop
$null = $process.CloseMainWindow()
if (-not $process.WaitForExit(10000)) {
    throw 'Runqian designer did not close normally'
}
```

After static readback, reopen the exact target:

```powershell
Start-Process `
  -FilePath 'D:\soft\raqsoft\report\bin\report.exe' `
  -ArgumentList '<absolute-target-rpx>' `
  -WindowStyle Normal
```

Wait until the process is responding and the designer title appears.

## Cleanup

The persistent wrapper keeps compiled classes in the machine-local LocalAppData cache. Do not delete and recreate them per task. Confirm that only the intended RPX was created or modified and that the project contains no task-generated Java source, class file, patch JSON, backup, or intermediate business template.
