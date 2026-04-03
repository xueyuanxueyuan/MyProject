---
name: "doc-converter"
description: "Converts Word documents to Markdown format, preserving images and formatting. Invoke when user needs to convert DOCX files to MD format or asks about document conversion tools."
---

# Document Converter Skill

This skill provides functionality to convert Word documents (DOCX) to Markdown format, preserving images, tables, and formatting elements.

## Location

The document conversion tools are located at:
`/home/source/App/DocstoMarkDown/`

## Available Tools

### 1. Python Script: `convert_docx_to_md.py`

**Description**: Converts DOCX files to Markdown using the doctomarkdown library.

**Usage**:
```bash
python convert_docx_to_md.py <input.docx> <output.md>
```

**Features**:
- Automatically saves output files to the `doc/` directory
- Preserves document structure and formatting
- Handles images and tables

### 2. Pandoc Command

**Description**: Uses the pandoc tool to convert DOCX files to Markdown.

**Usage**:
```bash
pandoc <input.docx> -o doc/<output.md>
```

**Features**:
- Powerful document conversion
- Preserves images in `media/` directory
- Handles complex formatting

## Step-by-Step Conversion Process

### Using the Python Script

1. **Navigate to the tool directory**:
   ```bash
   cd /home/source/App/DocstoMarkDown/
   ```

2. **Activate the virtual environment** (if needed):
   ```bash
   source doctomarkdown-env/bin/activate
   ```

3. **Run the conversion script**:
   ```bash
   python convert_docx_to_md.py input.docx output.md
   ```

4. **Check the output**:
   The converted Markdown file will be saved in the `doc/` directory.

### Using Pandoc

1. **Navigate to the tool directory**:
   ```bash
   cd /home/source/App/DocstoMarkDown/
   ```

2. **Run the pandoc command**:
   ```bash
   pandoc input.docx -o doc/output.md
   ```

3. **Check the output**:
   The converted Markdown file will be saved in the `doc/` directory, and images will be extracted to the `media/` directory.

## Example Usage

### Converting a Document

**Input**:
```bash
# Using Python script
python convert_docx_to_md.py report.docx report.md

# Using Pandoc
pandoc report.docx -o doc/report.md
```

**Output**:
```
Successfully converted report.docx to doc/report.md
```

## Notes

1. **File Paths**:
   - Input files should be in the current directory or specified with a full path
   - Output files are automatically saved to the `doc/` directory

2. **Image Handling**:
   - Pandoc extracts images to a `media/` directory
   - The Python script handles images according to the doctomarkdown library's functionality

3. **Formatting**:
   - Both methods preserve basic formatting (headings, lists, tables, bold/italic)
   - Complex formatting may require manual adjustment

4. **Dependencies**:
   - Python script requires the doctomarkdown library (installed in the virtual environment)
   - Pandoc requires the pandoc tool to be installed on the system

## Troubleshooting

- **Permission errors**: Ensure you have write access to the `doc/` directory
- **Missing dependencies**: Activate the virtual environment or install required packages
- **Conversion issues**: For complex documents, try using Pandoc instead of the Python script

## Best Practices

1. **Backup original files** before conversion
2. **Test with small documents** first
3. **Check the output** for formatting issues
4. **Use Pandoc** for more complex documents
5. **Use the Python script** for simpler documents or when you need more control

This skill provides a reliable way to convert Word documents to Markdown format, preserving important elements while creating clean, usable Markdown files.