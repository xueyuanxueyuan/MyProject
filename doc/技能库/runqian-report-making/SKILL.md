---
name: runqian-report-making
description: Create, modify, inspect, validate, and preview Runqian/Raqsoft reports end to end, including existing `.rpx` templates, requirement prototypes, report datasets and parameters, snapshot-based printing, A4 layouts, and frontend preview integration. Use for requests mentioning 润乾报表, Runqian, Raqsoft, RPX, 报表绘制, 报表打印, 打印预览, 模板修改, 数据集, 合并表头, 版式调整, or converting a Vue table or requirement prototype into a Runqian report.
---

# Runqian Report Making

Build the actual report, not only an implementation document. Preserve the business contract while making the printed result match its authoritative prototype.

## Local Environment

Before using Runqian Java libraries or the designer:

1. Read `references/rpx-object-model.md`.
2. Treat `config/local.json` as the persistent, confirmed contract for this machine. Run `scripts/validate-local-environment.ps1` to validate it quickly at the start of a new conversation.
3. Run `scripts/detect-local-environment.ps1` only when `config/local.json` is absent. If validation proves that a recorded path is stale, review the installation and rerun with explicit `-Force`. Never rerun discovery merely because the conversation is new, and never overwrite a valid configuration.
4. Review every newly detected path. If discovery is incomplete or ambiguous, inspect the local machine and edit `config/local.json`; never copy paths from another computer blindly.
5. Keep `config/local.json` machine-local. Do not include it when sharing or repackaging this skill.
6. Fail clearly when Java, the designer, required JAR directories, or a usable license cannot be confirmed.

Use `scripts/invoke-rpx-tool.ps1` for repeatable object-model inspection and surgical patches. Its Java source is persistent inside this skill and its compiled cache lives under LocalAppData. Do not generate task-specific `.java`, `.class`, patch JSON, or inspection source files in a project workspace when this tool can express the operation.

For installation and handoff instructions, read `使用说明.md`.

## Authority And Scope

Separate appearance authority from data authority before editing:

1. Follow the user's latest explicit instruction.
2. For appearance, follow the specifically named requirement prototype, sample report, or acceptance image.
3. For data, follow the implemented request/response DTO, snapshot schema, page transformations, and confirmed business rules.
4. Use the current Vue page as appearance authority only when no stronger prototype exists.
5. Use similar RPX templates only for repository conventions, never to override this report's requirements.

If sources conflict, state the conflict and resolve it from the hierarchy above. Do not silently combine incompatible designs.

Choose the requested scope:

- If asked for a plan or document, produce guidance without modifying RPX.
- If asked to create, draw, update, fix, or continue a report, modify the actual target RPX.
- If asked only to diagnose or review, inspect and report findings without writing.
- If SQL changes are involved, also apply the SQL performance guardrail skill and verify live metadata through DBX before relying on source assumptions.
- If Vue or Java changes are involved, also apply the mandatory project frontend or backend conventions.

## Protect The Workspace

Before any write:

1. Read applicable `AGENTS.md`, handoff documents, and active checkpoints.
2. Identify the exact target RPX path and whether the user requires in-place modification.
3. Inspect Git status in the real repository root. Preserve all unrelated modified and untracked files.
4. Do not create another business template, backup, test file, or alternate RPX unless explicitly requested.
5. Use the persistent `scripts/invoke-rpx-tool.ps1` object-model tool. Do not create Java source, class files, patch JSON, backups, or inspection utilities in the project workspace. Use an external artifact only when the persistent tool cannot express a required operation, explain that gap first, and keep the artifact in the machine-local skill/cache area.
6. Never replace a report's data contract merely to make preview data easier to obtain.

For Zaozhuang voucher-attachment reports matching `jshs-hzpz-pzfj-*.rpx`, treat `report/raqsoft-zaozhuang` as the only maintained template directory. Do not create, modify, or restore matching templates under `report/raqsoft`; if duplicates exist there, remove them only when the user authorizes deletion.

## End-To-End Workflow

### 1. Read The Requirement Artifact

Inspect every authoritative artifact, not only copied text:

- Render or extract DOCX/PDF pages when layout matters.
- View embedded prototype images at original resolution.
- Extract table labels, merge groups, title patterns, date/unit positions, sample formats, totals, and visual grouping.
- Ignore colors only when the user explicitly says colors do not matter.

Write a concrete cell matrix before drawing: title row, auxiliary row, header rows, detail expansion row, optional total row, and footer row. Record exact merge ranges and dynamic labels.

### 2. Trace The Data Contract

Locate the page's print action, request DTO, response DTO, backend assembly, snapshot writer, and report dataset contract.

Confirm:

- Required report parameters and their types.
- Whether printing reads a public snapshot or re-queries business tables.
- Header fields, detail fields, row order, row type, and null semantics.
- Dictionary/name sources and frontend formatters.
- Amount, percentage, date, and empty-value formats.

For snapshot reports, prefer one immutable snapshot identifier as the print parameter. The RPX must render the Java-assembled result instead of recalculating business logic or silently filling missing values.

### 3. Inspect The Existing RPX With Official APIs

Use Runqian's official object model as the authority for logical structure. Read [references/rpx-object-model.md](references/rpx-object-model.md) before object-model writes.

Inspect RPX files with the persistent local tool:

```powershell
& '<skill>/scripts/invoke-rpx-tool.ps1' -Action Inspect -Path '<absolute-rpx-path>'
```

At minimum inspect:

- Logical row and column count.
- Column widths and row heights.
- Both column visibility (`getColVisible`) and each cell's hidden-column flag (`getColHidden`); these are independent settings.
- Cell values and expression maps.
- Merge areas, including subordinate cells that may read as `null`.
- Fonts, bold, alignment, wrap, adjust-size mode, borders, and background.
- Parameter metadata and dataset metadata.
- Print setup, margins, orientation, zoom mode, and alignment.

Binary scanners or `cli-anything-raqsoft-rpx` may report storage-level header counts that differ from logical counts. Use them only as secondary evidence; do not delete a row or column until `ReportUtils.read()` confirms the logical structure.

### 4. Close The Designer Before Writing

The Runqian designer is a caching writer. Before object-model modification:

1. Check whether `report.exe` is open.
2. Close it normally with `CloseMainWindow()` and wait for exit.
3. Surface an unsaved-changes prompt or timeout; do not force-kill unless explicitly authorized.
4. Write the RPX only after the designer exits.

After writing and static readback, reopen the exact same RPX for user inspection.

### 5. Modify Surgically

Load the installed license before `ReportUtils.read()`. Guard the expected logical dimensions and fail if they do not match.

For supported changes, pass an in-memory JSON patch to the persistent tool. Every patch must declare `expectedRows`, `expectedCols`, structural guards, and explicit operations. The tool preserves parameters, datasets, print setup, row/column metadata, and all untouched cells by serialized fingerprint comparison before overwriting the RPX.

```powershell
$patch = @{
    expectedRows = 6
    expectedCols = 9
    guards = @(@{ cell = 'A5'; value = '合计'; merge = 'A5:B5' })
    operations = @(
        @{ type = 'merge'; range = 'G5:H5'; source = 'H5' },
        @{ type = 'border'; range = 'A5:I5'; style = 'solid'; width = 0.75; color = -16777216 }
    )
} | ConvertTo-Json -Depth 8 -Compress
& '<skill>/scripts/invoke-rpx-tool.ps1' -Action Patch -TargetPath '<absolute-rpx-path>' -PatchJson $patch
```

Supported operation types are `set`, `merge`, and `border`. Extend the persistent tool in the skill when a new reusable object-model operation is required; do not rewrite a one-off Java utility in the project.

Preserve datasets, parameters, detail expressions, formats, and print settings unless they are in scope. When rebuilding only the title/header, replace only those rows.

Merged-cell rules:

- Set styling on every cell in the merged area, not only its anchor.
- Clearing `setMergedArea(null)` may leave old merge ownership behind.
- When merge geometry changes materially, replace affected cells with fresh `NormalCell` objects, then rebuild merges.
- Never replace the detail row when its expression map must be preserved.
- Null-check subordinate merged cells during readback.

Detail expansion rules:

- Use one sequence-producing expression such as `ds_rows.select(KEY_FIELD)` as the vertical master cell.
- Set every other cell in the same detail row to that cell through `setLeftHead(...)`, while preserving its own `ds_rows.FIELD` expression.
- Use `setTopHead(...)` only for horizontal expansion relationships.
- Re-read master expressions and left/top master properties after writing. Seeing only the first dataset record usually means the expansion master is scalar or the dependent cells are not bound to it.

Hidden-column rules:

- Do not infer that a column will render merely because `getColVisible()` is true. The designer's “隐藏列” checkbox is stored on cells through `INormalCell.getColHidden()`.
- For every column that must print, especially sequence columns and merged title/footer areas, scan every logical cell in that column or merged area and require `getColHidden() == false`.
- Include empty cells in the hidden-column scan. A hidden flag can remain on a cell with no value, expression, merge, or border.
- When clearing an accidental hidden state, change only `colHidden`, guard the original state, and verify cell content, expressions, merges, master-cell relationships, datasets, parameters, and print setup remain unchanged.

Use expressions for changing labels rather than hardcoding years or months. Typical dynamic content includes:

- `YYYY年M--M月份...` titles from start/end month fields.
- End-month last date using `pdate@me(date(...))`.
- `X月底余额` from the selected ending month.
- Operator and print date from the confirmed header contract.

Do not add hidden fallbacks, alternate fields, fixed-year branches, or recalculations that are absent from the business contract.

## Layout And Typography

### Prototype Fidelity

Match the prototype's:

- Literal title wording and punctuation.
- Date and unit positions.
- First-level groups, second-level labels, field order, and merge ranges.
- Presence or absence of condition, total, spacer, signature, and footer rows.
- Dynamic month wording.

Do not copy colors, shading, bold, or spacing that the user has explicitly excluded.

### Fonts

Use the user's named Chinese type size. In this Runqian installation, commonly used mappings are:

- 小二: 18pt
- 二号: 22pt
- 五号: 10pt in existing project templates
- 六号: 8pt

Default every Runqian report title to SimSun (`宋体`), Chinese Small Two (`18pt`), bold, horizontally centered, and vertically centered unless a newer user instruction or authoritative prototype explicitly overrides it. Apply these properties to every cell in the merged title area and verify them through object-model readback.

Treat the remaining size mappings as project conventions rather than universal typography law. Dense 20-column A4 reports often need six-size body text.

### Row Heights And Spacer Rows

- Preserve Runqian's default row height when the user asks for default sizing; in the current installation it is commonly `8`.
- Do not add blank spacer rows unless the prototype contains them.
- When removing a spacer row, verify footer expressions move to the intended row.
- Distinguish row height from column width; confirm ambiguous language from screenshots or current properties before changing geometry.

### Wrapping And Shrinking

Use the correct Runqian adjust mode:

- Fixed-size wrapping: `ADJUST_FIXED_WRAP` with `setTextWrap(true)`.
- No wrapping with shrink-to-fit: `ADJUST_SHRINK` with `setTextWrap(false)`.
- Fixed single line: `ADJUST_FIXED` with `setTextWrap(false)`.

Practical defaults for dense financial reports:

- Headers: fixed font with automatic wrapping; do not use shrink-to-fit unless explicitly requested.
- Amount detail cells: no wrapping; allow shrink-to-fit when needed.
- Names may wrap when the user permits it. Ratios and year-on-year detail cells should remain single-line when they include a literal `%`; use shrink-to-fit when the fixed column width cannot hold the full value.
- Keep one global amount unit line when the prototype has one; remove repeated units from column labels only when required.

### Page Fit

Calculate total visible column width against printable paper width. For A4 landscape, account for left and right margins before choosing widths or scale.

- Prefer content-aware column widths.
- Use fit-to-width when the report must occupy one page horizontally.
- Use fixed zoom only when the target renderer's behavior is confirmed.
- Do not widen columns merely to remove unused page space if the user asks to preserve them.
- Recheck that long values remain legible after scaling.

## Dataset And Formatting Rules

- Use the minimum datasets required by the real report.
- Add a total dataset or row only when the page/prototype contains a total or the user requests one.
- Keep SQL placeholder order aligned with dataset parameters.
- Do not use `NVL`, `COALESCE`, code-as-name aliases, guessed mappings, or alternate sources for business-critical fields unless the business rule explicitly defines them.
- Keep unknown or missing values empty rather than converting them to zero.
- Amount cells use formats such as `#,##0.00`.
- Percentage and ratio values use the format required by the page/prototype; keep numeric values numeric. When snapshot values are already percentage points such as `19.42`, use a literal-percent format such as `0.00'%'` instead of `0.00%`, which would display `1942.00%`.
- Real interest-rate fields must display exactly two decimals.

## Frontend Integration

Use the existing `openRaqsoftPreview` helper when available.

- Validate required query state before printing.
- Pass the confirmed report name and explicit parameters.
- For snapshot printing, pass only the valid snapshot ID when that is the contract.
- Invalidate the snapshot ID when query conditions change, reset, or fail.
- Surface blocked popups and malformed report responses directly.

Do not add frontend display fallbacks for missing business fields.

## Verification Levels

Always perform static integrity verification after a write, even when the user will do visual acceptance:

1. Re-read the saved RPX with `ReportUtils.read()`.
2. Confirm logical dimensions and intended merge ranges.
3. Confirm all expected detail expressions remain.
4. Confirm parameter and dataset counts, names, SQL, and parameter expressions.
5. Confirm fonts, bold, alignment, wrap modes, row heights, column widths, and print setup for representative cells.
6. Confirm required printable cells have `colHidden=false`; verify this independently from column `visible=true`.
7. Confirm no second business RPX, backup, or project-local source/class/patch file was created.
8. Reopen the same RPX in the designer.

Run real data/snapshot preview only when requested or when it remains part of the user's acceptance scope. If the user explicitly opts out, do not perform database, snapshot, or preview validation; report that boundary accurately.

## Failure Patterns To Avoid

- Editing while the designer is open, then losing changes to its cached save.
- Treating storage-level row/column counts as logical counts.
- Guessing a prototype from a partial screenshot when the source artifact is available.
- Matching the Vue page's groups when a stronger requirement prototype differs.
- Clearing old merges without replacing cells, leaving stale merge ownership.
- Styling only the anchor of a merged title.
- Applying shrink-to-fit to every header and creating visibly tiny text.
- Forcing five-size text into a dense 20-column A4 report.
- Confusing row height with column width.
- Adding a blank spacer row that is absent from the prototype.
- Repeating units in every column when a global unit label already exists.
- Hardcoding a sample year, month, title, or date.
- Replacing snapshot rendering with new SQL aggregation.
- Claiming preview success after only structural readback.

## Implementation Documents

When the requested deliverable is a Markdown implementation document, include only applicable sections:

1. Report file and authority sources
2. Page/prototype and data contract
3. Datasets and SQL
4. Dataset parameters
5. Cell matrix and merge ranges
6. Dynamic expressions and formats
7. Print setup
8. Frontend integration
9. Verification checklist and unresolved blockers

Put each dataset's SQL before its single corresponding parameter list. State the parameter count. Do not invent unused summary sections.
