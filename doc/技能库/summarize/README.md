# Summarize Skill for OpenClaw

A skill that provides fast CLI summarization for URLs, local files, and YouTube links.

## Features

- Summarize web pages, PDFs, images, audio, and YouTube videos
- Support for multiple AI models (OpenAI, Anthropic, xAI, Google)
- Configurable output length and format
- JSON output support for machine-readable results

## Installation

First, install the `summarize` CLI tool:

```bash
brew install steipete/tap/summarize
```

## Usage

```bash
# Summarize a URL
summarize "https://example.com" --model google/gemini-3-flash-preview

# Summarize a local file
summarize "/path/to/file.pdf" --model google/gemini-3-flash-preview

# Summarize a YouTube video
summarize "https://youtu.be/dQw4w9WgXcQ" --youtube auto
```

## Configuration

Set API keys for your chosen provider:
- OpenAI: `OPENAI_API_KEY`
- Anthropic: `ANTHROPIC_API_KEY`
- xAI: `XAI_API_KEY`
- Google: `GEMINI_API_KEY` (also accepts `GOOGLE_GENERATIVE_AI_API_KEY`, `GOOGLE_API_KEY`)

Default model: `google/gemini-3-flash-preview`

Optional config file: `~/.summarize/config.json`

```json
{
  "model": "openai/gpt-5.2"
}
```

## Useful Flags

- `--length short|medium|long|xl|xxl|<chars>` - Control output length
- `--max-output-tokens <count>` - Set maximum output tokens
- `--extract-only` (URLs only) - Extract content without summarization
- `--json` - Output in JSON format
- `--firecrawl auto|off|always` - Use Firecrawl for blocked sites
- `--youtube auto` - Use Apify fallback for YouTube (requires `APIFY_API_TOKEN`)

## Optional Services

- `FIRECRAWL_API_KEY` - For sites that block standard scraping
- `APIFY_API_TOKEN` - For YouTube transcription fallback

## License

MIT

## Links

- Homepage: https://summarize.sh
- GitHub: https://github.com/lorissun2025/summarize-skill
