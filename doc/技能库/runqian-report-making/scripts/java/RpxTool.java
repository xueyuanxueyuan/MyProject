import com.raqsoft.report.model.NormalCell;
import com.raqsoft.report.model.engine.ExtCellSet;
import com.raqsoft.report.usermodel.INormalCell;
import com.raqsoft.report.usermodel.IReport;
import com.raqsoft.report.usermodel.PrintSetup;
import com.raqsoft.report.usermodel.DataSetConfig;
import com.raqsoft.report.usermodel.SQLDataSetConfig;
import com.raqsoft.report.util.ReportUtils;
import com.scudata.common.Area;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RpxTool {
    private RpxTool() {
    }

    private static int[] parseCell(String reference) {
        String value = reference.trim().toUpperCase();
        int split = 0;
        while (split < value.length() && Character.isLetter(value.charAt(split))) {
            split++;
        }
        if (split == 0 || split == value.length()) {
            throw new IllegalArgumentException("Invalid cell reference: " + reference);
        }
        int col = 0;
        for (int index = 0; index < split; index++) {
            col = col * 26 + value.charAt(index) - 'A' + 1;
        }
        return new int[]{Integer.parseInt(value.substring(split)), col};
    }

    private static int[] parseRange(String reference) {
        String[] cells = reference.split(":", -1);
        int[] begin = parseCell(cells[0]);
        int[] end = cells.length == 1 ? begin : parseCell(cells[1]);
        if (cells.length > 2 || begin[0] > end[0] || begin[1] > end[1]) {
            throw new IllegalArgumentException("Invalid range: " + reference);
        }
        return new int[]{begin[0], begin[1], end[0], end[1]};
    }

    private static String cellName(int row, int col) {
        StringBuilder result = new StringBuilder();
        int value = col;
        while (value > 0) {
            value--;
            result.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return result.append(row).toString();
    }

    private static String areaName(Area area) {
        if (area == null) {
            return null;
        }
        return cellName(area.getBeginRow(), area.getBeginCol()) + ":"
                + cellName(area.getEndRow(), area.getEndCol());
    }

    private static Object expression(INormalCell cell) {
        return cell.getExpMap() == null ? null : cell.getExpMap().get(INormalCell.VALUE);
    }

    private static String hash(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte item : digest) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private static JSONObject border(INormalCell cell) {
        return new JSONObject()
                .put("left", new JSONArray().put(cell.getLBStyle()).put(cell.getLBWidth()).put(cell.getLBColor()))
                .put("right", new JSONArray().put(cell.getRBStyle()).put(cell.getRBWidth()).put(cell.getRBColor()))
                .put("top", new JSONArray().put(cell.getTBStyle()).put(cell.getTBWidth()).put(cell.getTBColor()))
                .put("bottom", new JSONArray().put(cell.getBBStyle()).put(cell.getBBWidth()).put(cell.getBBColor()));
    }

    private static JSONObject printSetup(IReport report) {
        PrintSetup print = report.getPrintSetup();
        if (print == null) {
            throw new IllegalStateException("Report print setup is missing");
        }
        String orientation;
        if (print.getOrientation() == PrintSetup.PORTRAIT) {
            orientation = "portrait";
        } else if (print.getOrientation() == PrintSetup.LANDSCAPE) {
            orientation = "landscape";
        } else {
            orientation = "unknown";
        }
        return new JSONObject()
                .put("paper", print.getPaper())
                .put("paperName", print.getPaper() == PrintSetup.A4_PAPERSIZE ? "A4" : "other")
                .put("paperWidth", print.getPaperWidth())
                .put("paperHeight", print.getPaperHeight())
                .put("orientation", print.getOrientation())
                .put("orientationName", orientation)
                .put("leftMargin", print.getLeftMargin())
                .put("rightMargin", print.getRightMargin())
                .put("topMargin", print.getTopMargin())
                .put("bottomMargin", print.getBottomMargin())
                .put("zoomMode", print.getZoomMode())
                .put("zoomPageWidth", print.getZoomPageWidth())
                .put("zoomPageHeight", print.getZoomPageHeight())
                .put("zoomScale", print.getZoomScale())
                .put("hAlign", print.getHAlign())
                .put("vAlign", print.getVAlign());
    }

    private static JSONArray datasets(IReport report) {
        JSONArray result = new JSONArray();
        for (int index = 0; index < report.getDataSetMetaData().getDataSetConfigCount(); index++) {
            DataSetConfig config = report.getDataSetMetaData().getDataSetConfig(index);
            JSONObject item = new JSONObject()
                    .put("index", index)
                    .put("name", config.getName())
                    .put("type", config.getClass().getName())
                    .put("dataSource", config.getDataSourceName() == null
                            ? JSONObject.NULL : config.getDataSourceName());
            if (config instanceof SQLDataSetConfig) {
                SQLDataSetConfig sql = (SQLDataSetConfig) config;
                JSONArray parameters = new JSONArray();
                for (int parameter = 0; parameter < sql.getParamCount(); parameter++) {
                    parameters.put(new JSONObject()
                            .put("index", parameter)
                            .put("expression", sql.getParamExp(parameter) == null
                                    ? JSONObject.NULL : sql.getParamExp(parameter))
                            .put("type", sql.getParamType(parameter)));
                }
                item.put("sql", sql.getSQL() == null ? JSONObject.NULL : sql.getSQL())
                        .put("parameters", parameters);
            }
            result.put(item);
        }
        return result;
    }

    private static JSONObject inspect(IReport report, String path) throws Exception {
        JSONObject result = new JSONObject()
                .put("path", path)
                .put("rows", report.getRowCount())
                .put("cols", report.getColCount())
                .put("parameterCount", report.getParamMetaData().getParamCount())
                .put("datasetCount", report.getDataSetMetaData().getDataSetConfigCount())
                .put("datasets", datasets(report))
                .put("parameterHash", hash(report.getParamMetaData().serialize()))
                .put("datasetHash", hash(report.getDataSetMetaData().serialize()))
                .put("printSetup", printSetup(report))
                .put("printSetupHash", hash(report.getPrintSetup().serialize()));
        JSONArray rows = new JSONArray();
        for (int row = 1; row <= report.getRowCount(); row++) {
            rows.put(new JSONObject()
                    .put("row", row)
                    .put("height", report.getRowCell(row).getRowHeight())
                    .put("type", report.getRowCell(row).getRowType()));
        }
        JSONArray cols = new JSONArray();
        for (int col = 1; col <= report.getColCount(); col++) {
            cols.put(new JSONObject()
                    .put("col", col)
                    .put("width", report.getColCell(col).getColWidth())
                    .put("type", report.getColCell(col).getColType())
                    .put("visible", report.getColCell(col).getColVisible())
                    .put("visibleExpression", report.getColCell(col).getColVisibleExp() == null
                            ? JSONObject.NULL : report.getColCell(col).getColVisibleExp())
                    .put("autoWidth", report.getColCell(col).getAutoWidth())
                    .put("breakPage", report.getColCell(col).getBreakPage())
                    .put("breakColumn", report.getColCell(col).getBreakColumn()));
        }
        JSONArray cells = new JSONArray();
        for (int row = 1; row <= report.getRowCount(); row++) {
            for (int col = 1; col <= report.getColCount(); col++) {
                INormalCell cell = report.getCell(row, col);
                if (cell == null) {
                    continue;
                }
                Object exp = expression(cell);
                boolean hasBorder = cell.getLBStyle() != INormalCell.LINE_NONE
                        || cell.getRBStyle() != INormalCell.LINE_NONE
                        || cell.getTBStyle() != INormalCell.LINE_NONE
                        || cell.getBBStyle() != INormalCell.LINE_NONE;
                if (cell.getValue() == null && exp == null && cell.getMergedArea() == null
                        && !hasBorder && !cell.getColHidden()) {
                    continue;
                }
                cells.put(new JSONObject()
                        .put("cell", cellName(row, col))
                        .put("value", cell.getValue() == null ? JSONObject.NULL : cell.getValue())
                        .put("expression", exp == null ? JSONObject.NULL : exp)
                        .put("format", cell.getFormat() == null ? JSONObject.NULL : cell.getFormat())
                        .put("merge", cell.getMergedArea() == null ? JSONObject.NULL : areaName(cell.getMergedArea()))
                        .put("leftHead", cell.getLeftHead() == null ? JSONObject.NULL : cell.getLeftHead())
                        .put("topHead", cell.getTopHead() == null ? JSONObject.NULL : cell.getTopHead())
                        .put("fontName", cell.getFontName())
                        .put("fontSize", cell.getFontSize())
                        .put("bold", cell.isBold())
                        .put("colHidden", cell.getColHidden())
                        .put("hAlign", cell.getHAlign())
                        .put("vAlign", cell.getVAlign())
                        .put("wrap", cell.getTextWrap())
                        .put("adjust", cell.getAdjustSizeMode())
                        .put("border", border(cell)));
            }
        }
        return result.put("rowMetadata", rows).put("colMetadata", cols).put("cells", cells);
    }

    private static INormalCell ensureCell(IReport report, int row, int col) {
        INormalCell cell = report.getCell(row, col);
        if (cell == null) {
            cell = new NormalCell();
            report.setCell(row, col, cell);
        }
        return cell;
    }

    private static void assertGuard(IReport report, JSONObject guard) {
        int[] coordinate = parseCell(guard.getString("cell"));
        INormalCell cell = report.getCell(coordinate[0], coordinate[1]);
        if (cell == null) {
            throw new IllegalStateException("Guard cell is null: " + guard.getString("cell"));
        }
        if (guard.has("value") && !guard.optString("value", "").equals(String.valueOf(cell.getValue()))) {
            throw new IllegalStateException("Guard value mismatch at " + guard.getString("cell"));
        }
        if (guard.has("expression") && !guard.optString("expression", "").equals(String.valueOf(expression(cell)))) {
            throw new IllegalStateException("Guard expression mismatch at " + guard.getString("cell"));
        }
        if (guard.has("merge") && !guard.optString("merge", "").equals(areaName(cell.getMergedArea()))) {
            throw new IllegalStateException("Guard merge mismatch at " + guard.getString("cell"));
        }
        if (guard.has("colHidden") && guard.getBoolean("colHidden") != cell.getColHidden()) {
            throw new IllegalStateException("Guard hidden-column state mismatch at " + guard.getString("cell"));
        }
    }

    private static byte lineStyle(String value) {
        switch (value.toLowerCase()) {
            case "none": return INormalCell.LINE_NONE;
            case "dotted": return INormalCell.LINE_DOTTED;
            case "dashed": return INormalCell.LINE_DASHED;
            case "solid": return INormalCell.LINE_SOLID;
            case "double": return INormalCell.LINE_DOUBLE;
            case "dotdot": return INormalCell.LINE_DOTDOT;
            default: throw new IllegalArgumentException("Unknown line style: " + value);
        }
    }

    private static void applyBorder(INormalCell cell, JSONObject operation) {
        byte style = lineStyle(operation.optString("style", "solid"));
        float width = operation.optFloat("width", 0.75f);
        int color = operation.optInt("color", NormalCell.COLOR_BLACK);
        cell.setLBStyle(style); cell.setLBWidth(width); cell.setLBColor(color);
        cell.setRBStyle(style); cell.setRBWidth(width); cell.setRBColor(color);
        cell.setTBStyle(style); cell.setTBWidth(width); cell.setTBColor(color);
        cell.setBBStyle(style); cell.setBBWidth(width); cell.setBBColor(color);
    }

    private static void applySet(INormalCell cell, JSONObject operation) {
        if (operation.has("value")) {
            cell.setValue(operation.isNull("value") ? null : operation.get("value"));
        }
        if (operation.has("expression")) {
            cell.getExpMap(true).put(INormalCell.VALUE,
                    operation.isNull("expression") ? null : operation.get("expression"));
        }
        if (operation.has("format")) cell.setFormat(operation.optString("format", null));
        if (operation.has("fontName")) cell.setFontName(operation.getString("fontName"));
        if (operation.has("fontSize")) cell.setFontSize((short) operation.getInt("fontSize"));
        if (operation.has("bold")) cell.setBold(operation.getBoolean("bold"));
        if (operation.has("colHidden")) cell.setColHidden(operation.getBoolean("colHidden"));
        if (operation.has("hAlign")) cell.setHAlign((byte) operation.getInt("hAlign"));
        if (operation.has("vAlign")) cell.setVAlign((byte) operation.getInt("vAlign"));
        if (operation.has("wrap")) cell.setTextWrap(operation.getBoolean("wrap"));
        if (operation.has("adjust")) cell.setAdjustSizeMode((byte) operation.getInt("adjust"));
        if (operation.has("leftHead")) cell.setLeftHead(operation.optString("leftHead", null));
        if (operation.has("topHead")) cell.setTopHead(operation.optString("topHead", null));
    }

    private static void applyAddSqlDataset(IReport report, JSONObject operation) {
        int expectedCount = operation.getInt("expectedDatasetCount");
        int actualCount = report.getDataSetMetaData().getDataSetConfigCount();
        if (actualCount != expectedCount) {
            throw new IllegalStateException("Expected dataset count " + expectedCount + ", actual " + actualCount);
        }

        String name = operation.getString("name").trim();
        String sql = operation.getString("sql").trim();
        String dataSource = operation.getString("dataSource");
        JSONArray parameters = operation.getJSONArray("parameters");
        if (name.isEmpty() || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL dataset name and SQL must not be empty");
        }
        for (int index = 0; index < actualCount; index++) {
            if (name.equals(report.getDataSetMetaData().getDataSetConfig(index).getName())) {
                throw new IllegalStateException("Dataset already exists: " + name);
            }
        }

        SQLDataSetConfig dataset = new SQLDataSetConfig();
        dataset.setName(name);
        dataset.setDataSourceName(dataSource);
        dataset.setSQL(sql);
        for (int index = 0; index < parameters.length(); index++) {
            JSONObject parameter = parameters.getJSONObject(index);
            dataset.addParam(parameter.getString("expression"), (byte) parameter.getInt("type"));
        }
        report.getDataSetMetaData().addDataSetConfig(dataset);
    }

    private static void applyUpdateSqlDataset(IReport report, JSONObject operation) throws Exception {
        String name = operation.getString("name").trim();
        DataSetConfig config = report.getDataSetMetaData().getDataSetConfig(name);
        if (!(config instanceof SQLDataSetConfig)) {
            throw new IllegalStateException("SQL dataset does not exist: " + name);
        }
        SQLDataSetConfig dataset = (SQLDataSetConfig) config;
        String expectedSqlHash = operation.getString("expectedSqlHash");
        String actualSqlHash = hash(dataset.getSQL().getBytes(StandardCharsets.UTF_8));
        if (!expectedSqlHash.equals(actualSqlHash)) {
            throw new IllegalStateException("SQL dataset hash mismatch: " + name);
        }
        int expectedParamCount = operation.getInt("expectedParamCount");
        if (dataset.getParamCount() != expectedParamCount) {
            throw new IllegalStateException("SQL dataset parameter count mismatch: " + name);
        }
        String sql = operation.getString("sql").trim();
        if (sql.isEmpty()) {
            throw new IllegalArgumentException("Updated SQL must not be empty: " + name);
        }
        dataset.setSQL(sql);
    }

    private static Set<String> touchedCells(JSONArray operations) {
        Set<String> touched = new HashSet<>();
        for (int index = 0; index < operations.length(); index++) {
            JSONObject operation = operations.getJSONObject(index);
            if (!operation.has("range") && !operation.has("cell")) continue;
            String reference = operation.has("range") ? operation.getString("range") : operation.getString("cell");
            int[] range = parseRange(reference);
            for (int row = range[0]; row <= range[2]; row++) {
                for (int col = range[1]; col <= range[3]; col++) {
                    touched.add(cellName(row, col));
                }
            }
        }
        return touched;
    }

    private static void verifyUntouched(IReport before, IReport after, Set<String> touched,
                                        boolean allowDatasetChange) throws Exception {
        if (before.getRowCount() != after.getRowCount() || before.getColCount() != after.getColCount()) {
            throw new IllegalStateException("Logical dimensions changed");
        }
        if (!hash(before.getParamMetaData().serialize()).equals(hash(after.getParamMetaData().serialize()))
                || !hash(before.getPrintSetup().serialize()).equals(hash(after.getPrintSetup().serialize()))) {
            throw new IllegalStateException("Parameters or print setup changed outside the patch contract");
        }
        if (!allowDatasetChange
                && !hash(before.getDataSetMetaData().serialize()).equals(hash(after.getDataSetMetaData().serialize()))) {
            throw new IllegalStateException("Datasets changed outside the patch contract");
        }
        for (int row = 1; row <= before.getRowCount(); row++) {
            if (!hash(before.getRowCell(row).serialize()).equals(hash(after.getRowCell(row).serialize()))) {
                throw new IllegalStateException("Row metadata changed at row " + row);
            }
        }
        for (int col = 1; col <= before.getColCount(); col++) {
            if (!hash(before.getColCell(col).serialize()).equals(hash(after.getColCell(col).serialize()))) {
                throw new IllegalStateException("Column metadata changed at column " + col);
            }
        }
        for (int row = 1; row <= before.getRowCount(); row++) {
            for (int col = 1; col <= before.getColCount(); col++) {
                String name = cellName(row, col);
                if (touched.contains(name)) continue;
                INormalCell oldCell = before.getCell(row, col);
                INormalCell newCell = after.getCell(row, col);
                if (oldCell == null || newCell == null) {
                    if (oldCell != newCell) throw new IllegalStateException("Cell presence changed at " + name);
                } else if (!hash(oldCell.serialize()).equals(hash(newCell.serialize()))) {
                    throw new IllegalStateException("Untouched cell changed at " + name);
                }
            }
        }
    }

    private static void verifyMetadata(IReport before, IReport after, int expectedRows, int expectedCols,
                                       boolean allowPrintSetupChange, boolean allowDatasetChange) throws Exception {
        if (after.getRowCount() != expectedRows || after.getColCount() != expectedCols) {
            throw new IllegalStateException("Expected result " + expectedRows + "x" + expectedCols
                    + ", actual " + after.getRowCount() + "x" + after.getColCount());
        }
        if (!hash(before.getParamMetaData().serialize()).equals(hash(after.getParamMetaData().serialize()))) {
            throw new IllegalStateException("Parameters changed outside the patch contract");
        }
        if (!allowDatasetChange
                && !hash(before.getDataSetMetaData().serialize()).equals(hash(after.getDataSetMetaData().serialize()))) {
            throw new IllegalStateException("Datasets changed outside the patch contract");
        }
        if (!allowPrintSetupChange
                && !hash(before.getPrintSetup().serialize()).equals(hash(after.getPrintSetup().serialize()))) {
            throw new IllegalStateException("Print setup changed outside the patch contract");
        }
    }

    private static void verifyAddedSqlDatasets(IReport before, IReport after,
                                               List<JSONObject> additions) throws Exception {
        int originalCount = before.getDataSetMetaData().getDataSetConfigCount();
        if (after.getDataSetMetaData().getDataSetConfigCount() != originalCount + additions.size()) {
            throw new IllegalStateException("Unexpected dataset count after SQL dataset additions");
        }
        // 新增数据集只能追加，原有数据集的顺序和序列化内容必须保持不变。
        for (int index = 0; index < originalCount; index++) {
            DataSetConfig oldConfig = before.getDataSetMetaData().getDataSetConfig(index);
            DataSetConfig newConfig = after.getDataSetMetaData().getDataSetConfig(index);
            if (!hash(oldConfig.serialize()).equals(hash(newConfig.serialize()))) {
                throw new IllegalStateException("Existing dataset changed at index " + index);
            }
        }
        for (int additionIndex = 0; additionIndex < additions.size(); additionIndex++) {
            JSONObject expected = additions.get(additionIndex);
            DataSetConfig config = after.getDataSetMetaData().getDataSetConfig(originalCount + additionIndex);
            if (!(config instanceof SQLDataSetConfig)) {
                throw new IllegalStateException("Added dataset is not SQL: " + expected.getString("name"));
            }
            SQLDataSetConfig sqlConfig = (SQLDataSetConfig) config;
            if (!expected.getString("name").trim().equals(sqlConfig.getName())
                    || !expected.getString("dataSource").equals(sqlConfig.getDataSourceName())
                    || !expected.getString("sql").trim().equals(sqlConfig.getSQL())) {
                throw new IllegalStateException("Added SQL dataset metadata mismatch: " + expected.getString("name"));
            }
            JSONArray parameters = expected.getJSONArray("parameters");
            if (sqlConfig.getParamCount() != parameters.length()) {
                throw new IllegalStateException("Added SQL dataset parameter count mismatch: " + sqlConfig.getName());
            }
            for (int parameterIndex = 0; parameterIndex < parameters.length(); parameterIndex++) {
                JSONObject parameter = parameters.getJSONObject(parameterIndex);
                if (!parameter.getString("expression").equals(sqlConfig.getParamExp(parameterIndex))
                        || (byte) parameter.getInt("type") != sqlConfig.getParamType(parameterIndex)) {
                    throw new IllegalStateException("Added SQL dataset parameter mismatch: "
                            + sqlConfig.getName() + " index " + parameterIndex);
                }
            }
        }
    }

    private static void verifyUpdatedSqlDatasets(IReport before, IReport after,
                                                 List<JSONObject> updates) throws Exception {
        if (before.getDataSetMetaData().getDataSetConfigCount()
                != after.getDataSetMetaData().getDataSetConfigCount()) {
            throw new IllegalStateException("Dataset count changed during SQL update");
        }
        Set<String> updatedNames = new HashSet<>();
        for (JSONObject update : updates) {
            updatedNames.add(update.getString("name").trim());
        }
        for (int index = 0; index < before.getDataSetMetaData().getDataSetConfigCount(); index++) {
            DataSetConfig oldConfig = before.getDataSetMetaData().getDataSetConfig(index);
            DataSetConfig newConfig = after.getDataSetMetaData().getDataSetConfig(index);
            if (!updatedNames.contains(oldConfig.getName())) {
                if (!hash(oldConfig.serialize()).equals(hash(newConfig.serialize()))) {
                    throw new IllegalStateException("Unrelated dataset changed at index " + index);
                }
                continue;
            }
            if (!(oldConfig instanceof SQLDataSetConfig) || !(newConfig instanceof SQLDataSetConfig)) {
                throw new IllegalStateException("Updated dataset is not SQL: " + oldConfig.getName());
            }
            SQLDataSetConfig oldSql = (SQLDataSetConfig) oldConfig;
            SQLDataSetConfig newSql = (SQLDataSetConfig) newConfig;
            JSONObject expected = null;
            for (JSONObject update : updates) {
                if (oldConfig.getName().equals(update.getString("name").trim())) {
                    expected = update;
                    break;
                }
            }
            if (expected == null || !expected.getString("sql").trim().equals(newSql.getSQL())) {
                throw new IllegalStateException("Updated SQL mismatch: " + oldConfig.getName());
            }
            if (!oldSql.getName().equals(newSql.getName())
                    || !String.valueOf(oldSql.getDataSourceName()).equals(String.valueOf(newSql.getDataSourceName()))
                    || oldSql.getParamCount() != newSql.getParamCount()) {
                throw new IllegalStateException("SQL dataset metadata changed outside SQL text: " + oldConfig.getName());
            }
            for (int parameter = 0; parameter < oldSql.getParamCount(); parameter++) {
                if (!String.valueOf(oldSql.getParamExp(parameter)).equals(String.valueOf(newSql.getParamExp(parameter)))
                        || oldSql.getParamType(parameter) != newSql.getParamType(parameter)) {
                    throw new IllegalStateException("SQL dataset parameter changed: "
                            + oldConfig.getName() + " index " + parameter);
                }
            }
        }
    }

    private static JSONObject patch(String target, JSONObject spec) throws Exception {
        IReport report = ReportUtils.read(target);
        IReport before = (IReport) report.deepClone();
        int expectedRows = spec.getInt("expectedRows");
        int expectedCols = spec.getInt("expectedCols");
        if (report.getRowCount() != expectedRows || report.getColCount() != expectedCols) {
            throw new IllegalStateException("Expected " + expectedRows + "x" + expectedCols
                    + ", actual " + report.getRowCount() + "x" + report.getColCount());
        }
        JSONArray guards = spec.optJSONArray("guards");
        if (guards != null) {
            for (int index = 0; index < guards.length(); index++) assertGuard(report, guards.getJSONObject(index));
        }
        JSONArray operations = spec.getJSONArray("operations");
        Set<String> touched = touchedCells(operations);
        boolean structuralChange = false;
        boolean printSetupChange = false;
        List<JSONObject> addedSqlDatasets = new ArrayList<>();
        List<JSONObject> updatedSqlDatasets = new ArrayList<>();
        for (int index = 0; index < operations.length(); index++) {
            JSONObject operation = operations.getJSONObject(index);
            String type = operation.getString("type").toLowerCase();
            if ("set".equals(type)) {
                int[] coordinate = parseCell(operation.getString("cell"));
                applySet(ensureCell(report, coordinate[0], coordinate[1]), operation);
            } else if ("border".equals(type)) {
                int[] range = parseRange(operation.getString("range"));
                for (int row = range[0]; row <= range[2]; row++) {
                    for (int col = range[1]; col <= range[3]; col++) {
                        applyBorder(ensureCell(report, row, col), operation);
                    }
                }
            } else if ("merge".equals(type)) {
                int[] range = parseRange(operation.getString("range"));
                int[] sourceCoordinate = parseCell(operation.getString("source"));
                INormalCell source = report.getCell(sourceCoordinate[0], sourceCoordinate[1]);
                if (source == null) throw new IllegalStateException("Merge source is null: " + operation.getString("source"));
                Area area = new Area(range[0], range[1], range[2], range[3]);
                for (int row = range[0]; row <= range[2]; row++) {
                    for (int col = range[1]; col <= range[3]; col++) {
                        INormalCell clone = (INormalCell) source.deepClone();
                        clone.setMergedArea(area);
                        report.setCell(row, col, clone);
                    }
                }
            } else if ("copycell".equals(type)) {
                int[] coordinate = parseCell(operation.getString("cell"));
                int[] sourceCoordinate = parseCell(operation.getString("source"));
                INormalCell source = report.getCell(sourceCoordinate[0], sourceCoordinate[1]);
                if (source == null) throw new IllegalStateException("Copy source is null: " + operation.getString("source"));
                INormalCell clone = (INormalCell) source.deepClone();
                clone.setMergedArea(null);
                report.setCell(coordinate[0], coordinate[1], clone);
            } else if ("insertcolumn".equals(type)) {
                int column = operation.getInt("column");
                if (column < 1 || column > report.getColCount() + 1) {
                    throw new IllegalArgumentException("Invalid insert column: " + column);
                }
                report.insertCol(column);
                structuralChange = true;
            } else if ("columnwidth".equals(type)) {
                int column = operation.getInt("column");
                if (column < 1 || column > report.getColCount()) {
                    throw new IllegalArgumentException("Invalid column: " + column);
                }
                report.getColCell(column).setColWidth(operation.getFloat("width"));
                structuralChange = true;
            } else if ("printsetup".equals(type)) {
                PrintSetup print = report.getPrintSetup();
                if (operation.has("paper")) {
                    String paper = operation.getString("paper");
                    if (!"A4".equalsIgnoreCase(paper)) throw new IllegalArgumentException("Unsupported paper: " + paper);
                    print.setPaper(PrintSetup.A4_PAPERSIZE);
                }
                if (operation.has("orientation")) {
                    String orientation = operation.getString("orientation");
                    if ("portrait".equalsIgnoreCase(orientation)) print.setOrientation(PrintSetup.PORTRAIT);
                    else if ("landscape".equalsIgnoreCase(orientation)) print.setOrientation(PrintSetup.LANDSCAPE);
                    else throw new IllegalArgumentException("Unsupported orientation: " + orientation);
                }
                if (operation.has("zoomMode")) print.setZoomMode((byte) operation.getInt("zoomMode"));
                printSetupChange = true;
            } else if ("addsqldataset".equals(type)) {
                applyAddSqlDataset(report, operation);
                addedSqlDatasets.add(operation);
            } else if ("updatesqldataset".equals(type)) {
                applyUpdateSqlDataset(report, operation);
                updatedSqlDatasets.add(operation);
            } else {
                throw new IllegalArgumentException("Unknown operation type: " + type);
            }
        }

        // 先在内存中序列化并回读验证，验证通过后才覆盖正式 RPX。
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ReportUtils.write(output, report);
        byte[] bytes = output.toByteArray();
        IReport validated = ReportUtils.read(new ByteArrayInputStream(bytes));
        int expectedResultRows = spec.optInt("expectedResultRows", expectedRows);
        int expectedResultCols = spec.optInt("expectedResultCols", expectedCols);
        boolean datasetChange = !addedSqlDatasets.isEmpty() || !updatedSqlDatasets.isEmpty();
        verifyMetadata(before, validated, expectedResultRows, expectedResultCols,
                printSetupChange, datasetChange);
        if (!addedSqlDatasets.isEmpty()) verifyAddedSqlDatasets(before, validated, addedSqlDatasets);
        if (!updatedSqlDatasets.isEmpty()) verifyUpdatedSqlDatasets(before, validated, updatedSqlDatasets);
        if (!structuralChange && !printSetupChange) {
            verifyUntouched(before, validated, touched, datasetChange);
        }
        if (spec.optBoolean("dryRun", false)) {
            return inspect(validated, target).put("dryRun", true).put("touchedCells", new JSONArray(touched));
        }
        Files.write(Paths.get(target), bytes);
        IReport readBack = ReportUtils.read(target);
        verifyMetadata(before, readBack, expectedResultRows, expectedResultCols,
                printSetupChange, datasetChange);
        if (!addedSqlDatasets.isEmpty()) verifyAddedSqlDatasets(before, readBack, addedSqlDatasets);
        if (!updatedSqlDatasets.isEmpty()) verifyUpdatedSqlDatasets(before, readBack, updatedSqlDatasets);
        if (!structuralChange && !printSetupChange) {
            verifyUntouched(before, readBack, touched, datasetChange);
        }
        return inspect(readBack, target).put("patched", true).put("touchedCells", new JSONArray(touched));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: inspect <license> <rpx...> | patch <license> <rpx>");
        }
        ExtCellSet.readLicense(args[1]);
        if ("inspect".equals(args[0])) {
            JSONArray reports = new JSONArray();
            for (int index = 2; index < args.length; index++) {
                reports.put(inspect(ReportUtils.read(args[index]), args[index]));
            }
            System.out.println(new JSONObject()
                    .put("releaseDate", ExtCellSet.getReleaseDate())
                    .put("reports", reports).toString(2));
        } else if ("patch".equals(args[0]) && args.length == 3) {
            String specText = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println(patch(args[2], new JSONObject(specText)).toString(2));
        } else {
            throw new IllegalArgumentException("Unsupported command or argument count");
        }
    }
}
