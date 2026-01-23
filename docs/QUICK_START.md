# Quick Start

## Prerequisites

- Java 17+
- Gradle
- **LLM Provider API Key** (Required - depends on provider)

## LLM Provider Configuration

The application supports multiple LLM providers. You can configure which provider to use via environment variables.

### How It Works

```
┌─────────────────────────────────────────────────────────────┐
│  Application Startup                                        │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  Read LLM_PROVIDER environment variable                     │
│  Default: "openai"                                          │
└────────────────┬────────────────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
┌──────────────┐   ┌──────────────────────────────────────────┐
│ LLM_PROVIDER │   │ Which API key is required?               │
│ = openai     │   │                                          │
│ = anthropic  │   │ openai    → OPENAI_API_KEY               │
│ = google     │   │ anthropic → ANTHROPIC_API_KEY            │
│ = mistral    │   │ google    → GOOGLE_API_KEY               │
│ = deepseek   │   │ mistral   → MISTRAL_API_KEY              │
└──────────────┘   │ deepseek  → DEEPSEEK_API_KEY             │
                   └──────────────┬───────────────────────────┘
                                  │
                                  ▼
                   ┌──────────────────────────────────────────┐
                   │ Check for provider-specific API key      │
                   │ If not found, check LLM_API_KEY          │
                   └──────────────┬───────────────────────────┘
                                  │
                         ┌────────┴────────┐
                         │                 │
                      Found            Not Found
                         │                 │
                         ▼                 ▼
              ┌──────────────────┐  ┌─────────────────┐
              │ Initialize LLM   │  │ ERROR:          │
              │ with provider    │  │ API key missing │
              │ and model        │  │ Application     │
              │                  │  │ won't start     │
              └──────────────────┘  └─────────────────┘
```

### Configuration Variables

| Variable | Description | Default | Required? |
|----------|-------------|---------|-----------|
| `LLM_PROVIDER` | LLM provider name | `openai` | ❌ No |
| `LLM_MODEL_NAME` | Model name to use | Provider-specific default | ❌ No |
| `LLM_API_KEY` | Generic API key (alternative) | - | Depends on provider |

### Supported Providers

> **Important:** The application supports **ANY model** from each provider's API. The examples below are just common models. You are **not restricted** to these lists - use any model name that your provider supports!
>
> **Pricing Note:** If you use a model not listed in `model-pricing.json`, the system will use fallback pricing for cost tracking. This doesn't affect functionality - only the accuracy of cost estimates.

#### 1. OpenAI (Default)

**Environment Variables:**
```bash
export LLM_PROVIDER=openai
export LLM_MODEL_NAME=gpt-4o-mini          # Optional, defaults to gpt-4o-mini
export OPENAI_API_KEY=sk-...               # Required
```

**Or use generic variable:**
```bash
export LLM_PROVIDER=openai
export LLM_API_KEY=sk-...                  # Will be used as OPENAI_API_KEY
```

**Model Support:**
- ✅ **Any OpenAI model** - You can use ANY model supported by OpenAI's API
- The application has no restrictions on which models you can use
- Default model: `gpt-4o-mini`

**Common Examples:**
- `gpt-4o-mini`, `gpt-4o`, `gpt-4o-2024-11-20`
- `o1`, `o1-mini`, `o1-preview`
- `gpt-4-turbo`, `gpt-4`, `gpt-3.5-turbo`
- Or any other OpenAI model

**Note:** Pricing data is available for common models. For other models, fallback pricing will be used for cost tracking.

**Get API Key:** https://platform.openai.com/api-keys

---

#### 2. Anthropic (Claude)

**Environment Variables:**
```bash
export LLM_PROVIDER=anthropic
export LLM_MODEL_NAME=claude-3-5-sonnet-20241022  # Optional
export ANTHROPIC_API_KEY=sk-ant-...               # Required
```

**Or use generic variable:**
```bash
export LLM_PROVIDER=anthropic
export LLM_API_KEY=sk-ant-...              # Will be used as ANTHROPIC_API_KEY
```

**Model Support:**
- ✅ **Any Anthropic model** - You can use ANY model supported by Anthropic's API
- The application has no restrictions on which models you can use
- Default model: `claude-3-5-sonnet-20241022`

**Common Examples:**
- `claude-3-5-sonnet-20241022`, `claude-3-5-haiku-20241022`
- `claude-3-opus-20240229`, `claude-3-sonnet-20240229`, `claude-3-haiku-20240307`
- `claude-2.1`, `claude-2.0`, `claude-instant-1.2`
- Or any other Anthropic model

**Note:** Pricing data is available for common models. For other models, fallback pricing will be used for cost tracking.

**Get API Key:** https://console.anthropic.com/

---

#### 3. Google (Gemini)

**Environment Variables:**
```bash
export LLM_PROVIDER=google
export LLM_MODEL_NAME=gemini-1.5-pro       # Optional
export GOOGLE_API_KEY=AIza...              # Required
```

**Or use generic variable:**
```bash
export LLM_PROVIDER=google
export LLM_API_KEY=AIza...                 # Will be used as GOOGLE_API_KEY
```

**Model Support:**
- ✅ **Any Google Gemini model** - You can use ANY model supported by Google's API
- The application has no restrictions on which models you can use
- Default model: `gemini-1.5-pro`

**Common Examples:**
- `gemini-1.5-pro`, `gemini-1.5-flash`, `gemini-1.5-flash-8b`
- `gemini-2.0-flash-exp`, `gemini-exp-1206`
- `gemini-pro`, `gemini-pro-vision`
- Or any other Google Gemini model

**Note:** Pricing data is available for common models. For other models, fallback pricing will be used for cost tracking.

**Get API Key:** https://makersuite.google.com/app/apikey

---

#### 4. Mistral AI

**Environment Variables:**
```bash
export LLM_PROVIDER=mistral
export LLM_MODEL_NAME=mistral-large-latest # Optional
export MISTRAL_API_KEY=...                 # Required
```

**Or use generic variable:**
```bash
export LLM_PROVIDER=mistral
export LLM_API_KEY=...                     # Will be used as MISTRAL_API_KEY
```

**Model Support:**
- ✅ **Any Mistral AI model** - You can use ANY model supported by Mistral's API
- The application has no restrictions on which models you can use
- Default model: `mistral-large-latest`

**Common Examples:**
- `mistral-large-latest`, `mistral-small-latest`, `mistral-medium-latest`
- `open-mistral-7b`, `open-mixtral-8x7b`, `open-mixtral-8x22b`
- `codestral-latest`
- Or any other Mistral AI model

**Note:** Pricing data is available for common models. For other models, fallback pricing will be used for cost tracking.

**Get API Key:** https://console.mistral.ai/

---

#### 5. DeepSeek

**Environment Variables:**
```bash
export LLM_PROVIDER=deepseek
export LLM_MODEL_NAME=deepseek-chat        # Optional
export DEEPSEEK_API_KEY=...                # Required
```

**Or use generic variable:**
```bash
export LLM_PROVIDER=deepseek
export LLM_API_KEY=...                     # Will be used as DEEPSEEK_API_KEY
```

**Model Support:**
- ✅ **Any DeepSeek model** - You can use ANY model supported by DeepSeek's API
- The application has no restrictions on which models you can use
- Default model: `deepseek-chat`

**Common Examples:**
- `deepseek-chat`, `deepseek-coder`
- `deepseek-reasoner`
- Or any other DeepSeek model

**Note:** Pricing data is available for common models. For other models, fallback pricing will be used for cost tracking.

**Get API Key:** https://platform.deepseek.com/

---

### API Key Priority

The application looks for API keys in this order:

1. **Provider-specific environment variable** (e.g., `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`)
2. **Generic `LLM_API_KEY`** (will be mapped to provider-specific key)

**Example:**
```bash
# Both of these work for OpenAI:
export OPENAI_API_KEY=sk-...
# OR
export LLM_API_KEY=sk-...
```

### Mandatory API Keys by Provider

| Provider | Mandatory Environment Variable | Alternative |
|----------|-------------------------------|-------------|
| `openai` | `OPENAI_API_KEY` | `LLM_API_KEY` |
| `anthropic` | `ANTHROPIC_API_KEY` | `LLM_API_KEY` |
| `google` | `GOOGLE_API_KEY` | `LLM_API_KEY` |
| `mistral` | `MISTRAL_API_KEY` | `LLM_API_KEY` |
| `deepseek` | `DEEPSEEK_API_KEY` | `LLM_API_KEY` |

**Note:** If the provider-specific key is not set, the application will use `LLM_API_KEY` as a fallback.

---

### Pricing Configuration (Optional)

#### Portkey API Key (OPTIONAL)
Used for fetching real-time LLM pricing data from Portkey Models API.

```bash
export PORTKEY_API_KEY=your-portkey-api-key-here
```

**What happens if not provided:**
- The system will use a local pricing file (`model-pricing.json`) in the project root
- If the local file doesn't exist, it will fall back to hardcoded pricing for common models
- Pricing is used for cost tracking and usage analytics only

**Note:** The Portkey Models API is actually **free and doesn't require authentication**. However, if you want to use it, you can set the environment variable. Otherwise, the local pricing file will be used automatically.

Get your API key from: https://portkey.ai (if you choose to use it)

---

### Complete Configuration Summary

| Variable | Required? | Purpose | Default |
|----------|-----------|---------|---------|
| `LLM_PROVIDER` | ❌ No | Which LLM provider to use | `openai` |
| `LLM_MODEL_NAME` | ❌ No | Which model to use | Provider-specific default |
| `OPENAI_API_KEY` | ✅ **YES** (if using OpenAI) | OpenAI API access | - |
| `ANTHROPIC_API_KEY` | ✅ **YES** (if using Anthropic) | Anthropic API access | - |
| `GOOGLE_API_KEY` | ✅ **YES** (if using Google) | Google API access | - |
| `MISTRAL_API_KEY` | ✅ **YES** (if using Mistral) | Mistral API access | - |
| `DEEPSEEK_API_KEY` | ✅ **YES** (if using DeepSeek) | DeepSeek API access | - |
| `LLM_API_KEY` | ❌ No | Generic API key (fallback) | - |
| `PORTKEY_API_KEY` | ❌ No | Real-time pricing data | Uses local pricing file |

## Build the Project

```bash
gradle build
```

## Start the Web UI

### Option 1: Using OpenAI (Default)

1. Set your OpenAI API key (required):
```bash
export OPENAI_API_KEY=sk-your-api-key-here
```

2. (Optional) Customize the model:
```bash
export LLM_MODEL_NAME=gpt-4o-mini  # Default, can use gpt-4o, o1, etc.
```

3. (Optional) Set Portkey API key for real-time pricing:
```bash
export PORTKEY_API_KEY=your-portkey-api-key-here
```

4. Start the web UI:
```bash
./start-web-ui.sh
```

5. Open your browser:
```
http://localhost:8080
```

---

### Option 2: Using Anthropic (Claude)

1. Set your Anthropic API key (required):
```bash
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-your-api-key-here
```

2. (Optional) Customize the model:
```bash
export LLM_MODEL_NAME=claude-3-5-sonnet-20241022  # Or claude-3-5-haiku-20241022
```

3. Start the web UI:
```bash
./start-web-ui.sh
```

---

### Option 3: Using Google (Gemini)

1. Set your Google API key (required):
```bash
export LLM_PROVIDER=google
export GOOGLE_API_KEY=AIza-your-api-key-here
```

2. (Optional) Customize the model:
```bash
export LLM_MODEL_NAME=gemini-1.5-pro  # Or gemini-1.5-flash
```

3. Start the web UI:
```bash
./start-web-ui.sh
```

---

### Option 4: Using Mistral AI

1. Set your Mistral API key (required):
```bash
export LLM_PROVIDER=mistral
export MISTRAL_API_KEY=your-api-key-here
```

2. (Optional) Customize the model:
```bash
export LLM_MODEL_NAME=mistral-large-latest
```

3. Start the web UI:
```bash
./start-web-ui.sh
```

---

### Option 5: Using DeepSeek

1. Set your DeepSeek API key (required):
```bash
export LLM_PROVIDER=deepseek
export DEEPSEEK_API_KEY=your-api-key-here
```

2. (Optional) Customize the model:
```bash
export LLM_MODEL_NAME=deepseek-chat
```

3. Start the web UI:
```bash
./start-web-ui.sh
```

---

### Using Generic API Key Variable

You can also use the generic `LLM_API_KEY` variable instead of provider-specific keys:

```bash
export LLM_PROVIDER=anthropic
export LLM_API_KEY=sk-ant-your-api-key-here  # Will be used as ANTHROPIC_API_KEY
./start-web-ui.sh
```

---

That's it! The Planner Agent Web UI is now running with your chosen LLM provider.

## Customizing Model Pricing (Optional)

If you don't set `PORTKEY_API_KEY`, you can customize model pricing by creating a `model-pricing.json` file in the project root:

```json
{
  "openai": {
    "gpt-4o-mini": {
      "pricing_config": {
        "pay_as_you_go": {
          "request_token": { "price": 0.000015 },
          "response_token": { "price": 0.00006 }
        },
        "currency": "USD"
      }
    }
  }
}
```

**Note:** Prices are in **cents per token** (not dollars). For example:
- `0.000015` = $0.015 per 1K tokens = $15 per 1M tokens

---

## Quick Reference: Provider Setup

### One-Command Setup by Provider

**OpenAI (Default):**
```bash
export OPENAI_API_KEY=sk-... && ./start-web-ui.sh
```

**Anthropic:**
```bash
export LLM_PROVIDER=anthropic && export ANTHROPIC_API_KEY=sk-ant-... && ./start-web-ui.sh
```

**Google:**
```bash
export LLM_PROVIDER=google && export GOOGLE_API_KEY=AIza... && ./start-web-ui.sh
```

**Mistral:**
```bash
export LLM_PROVIDER=mistral && export MISTRAL_API_KEY=... && ./start-web-ui.sh
```

**DeepSeek:**
```bash
export LLM_PROVIDER=deepseek && export DEEPSEEK_API_KEY=... && ./start-web-ui.sh
```

---

## Frequently Asked Questions (FAQ)

### Can I use any model from my provider, or only the ones listed?

**Answer:** You can use **ANY model** that your provider supports!

The model lists in this documentation are just **examples** of commonly used models. There are **no code restrictions** on which models you can use.

**Example:**
```bash
# All of these work fine:
export LLM_MODEL_NAME=gpt-4o-mini              # Listed example
export LLM_MODEL_NAME=gpt-4-turbo-preview      # Not listed, but works!
export LLM_MODEL_NAME=gpt-4-0125-preview       # Not listed, but works!
export LLM_MODEL_NAME=any-future-openai-model  # Will work if OpenAI supports it
```

**The only consideration:** If you use a model not in `model-pricing.json`, the cost tracking will use fallback pricing. This doesn't affect functionality - only the accuracy of cost estimates in logs.

### What happens if I use a model without pricing data?

**Answer:** The application will work perfectly fine! It will just use fallback pricing for cost tracking:

1. **Functionality:** ✅ No impact - the model will work normally
2. **Cost Tracking:** ⚠️ Uses fallback pricing (e.g., gpt-4o-mini pricing as default)
3. **Logs:** You'll see a warning like: `"Using default fallback pricing for unknown model: your-model-name"`

**To fix:** Add your model to `model-pricing.json` or set `PORTKEY_API_KEY` for automatic pricing.

### Can I use models from providers not listed here?

**Answer:** Currently, the application is configured for these providers:
- OpenAI
- Anthropic
- Google
- Mistral AI
- DeepSeek

To add a new provider, you would need to:
1. Add the provider configuration in the code
2. Add pricing data for the provider's models

### Do I need PORTKEY_API_KEY?

**Answer:** No, it's completely optional!

- **Without it:** Uses local `model-pricing.json` file → Falls back to hardcoded pricing
- **With it:** Gets real-time pricing for 2,000+ models from Portkey API

Both approaches work fine. Portkey is only needed if you want the most up-to-date pricing data.

---

## Troubleshooting

### "API key not found" Error

**Problem:** Application fails to start with "API key not found" or similar error.

**Solution:**
1. Check that you've set the correct API key for your provider:
   - OpenAI: `OPENAI_API_KEY`
   - Anthropic: `ANTHROPIC_API_KEY`
   - Google: `GOOGLE_API_KEY`
   - Mistral: `MISTRAL_API_KEY`
   - DeepSeek: `DEEPSEEK_API_KEY`

2. Verify the environment variable is set:
   ```bash
   echo $OPENAI_API_KEY  # Should print your key
   ```

3. Make sure you're using the correct provider name:
   ```bash
   echo $LLM_PROVIDER  # Should be: openai, anthropic, google, mistral, or deepseek
   ```

### "Model not found" Error

**Problem:** Application fails with "model not found" or "model not supported".

**Solution:**
1. Check that the model name is correct for your provider (see supported models above)
2. Verify `LLM_MODEL_NAME` is set correctly:
   ```bash
   echo $LLM_MODEL_NAME
   ```
3. If not set, the application will use the provider's default model

### Pricing Data Issues

**Problem:** Pricing data shows as $0.00 or incorrect values.

**Solution:**
1. Set `PORTKEY_API_KEY` for real-time pricing, OR
2. Create a `model-pricing.json` file in the project root (see "Customizing Model Pricing" section)
3. Check logs for pricing-related warnings

---

## Environment Variables Cheat Sheet

```bash
# === LLM Provider Configuration ===
export LLM_PROVIDER=openai              # openai | anthropic | google | mistral | deepseek
export LLM_MODEL_NAME=gpt-4o-mini       # Model name (optional, uses provider default)

# === Provider-Specific API Keys (choose one based on LLM_PROVIDER) ===
export OPENAI_API_KEY=sk-...            # For OpenAI
export ANTHROPIC_API_KEY=sk-ant-...     # For Anthropic
export GOOGLE_API_KEY=AIza...           # For Google
export MISTRAL_API_KEY=...              # For Mistral
export DEEPSEEK_API_KEY=...             # For DeepSeek

# === OR use generic API key ===
export LLM_API_KEY=...                  # Will be mapped to provider-specific key

# === Optional: Pricing Configuration ===
export PORTKEY_API_KEY=...              # For real-time pricing (optional)

# === Start the application ===
./start-web-ui.sh
```

---

## Next Steps

- **Learn the architecture:** See [docs/EXECUTION_FLOW.md](EXECUTION_FLOW.md)
- **Quick reference:** See [docs/QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- **Customize pricing:** See [MODEL_PRICING_README.md](../MODEL_PRICING_README.md)
- **Add capabilities:** Review existing capabilities in `src/main/java/com/krista/kme/agent/planner/capabilities/`

