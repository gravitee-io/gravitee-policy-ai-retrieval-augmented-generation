# AI Semantic Caching Policy

[![Available at Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-ee/apim/plugins/policies/gravitee-policy-ai-semantic-caching/)[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-ai-semantic-caching/blob/master/LICENSE.txt)[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-ai-semantic-caching/releases)[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-ai-semantic-caching.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-ai-semantic-caching)

## Phases

| onRequest | onResponse | onMessageRequest | onMessageResponse |
|:---------:|:----------:|:----------------:|:-----------------:|
| ✅        |            |                  |                   |

## Description

The `ai-semantic-caching` policy enables **semantic caching** of responses based on the similarity of request content. It uses an embedding model to transform incoming requests into vector representations, then compares them against previously cached vectors in a vector store. If a similar context is found, the cached response can be reused, saving computation and latency.

This policy integrates with AI resources such as:
- **Text embedding models**
- **Vector stores**

Semantic caching decisions and storage can be customized using **Gravitee EL expressions**.

> ℹ️ This policy works best when used with stateless APIs or when identical responses can be safely reused for similar requests.

## Configuration

You can configure the policy with the following options:

| Property           | Required | Description                                                                                                      | Type   | Default                                               |
|--------------------|----------|------------------------------------------------------------------------------------------------------------------|--------|-------------------------------------------------------|
| `modelName`        | ✅        | The name of the AI embedding model resource to use.                                                              | string | —                                                     |
| `vectorStoreName`  | ✅        | The name of the vector store resource used to store and retrieve semantic embeddings.                            | string | —                                                     |
| `promptExpression` |          | EL expression to extract the content to embed (e.g. request body).                                               | string | `{#request.content}`                                  |
| `cacheCondition`   |          | EL expression that determines whether the response is cacheable.                                                 | string | `{#response.status >= 200 && #response.status < 300}` |
| `parameters`       |          | List of key-value pairs to store as metadata with the vector and/or in the query. Values support EL expressions. | array  | —                                                     |

### Parameter Object Structure

Each `parameter` item contains:

| Property | Description                                                                                    | Type    |
|----------|------------------------------------------------------------------------------------------------|---------|
| `key`    | Name of the metadata field.                                                                    | string  |
| `value`  | EL expression to extract the value from the context.                                           | string  |
| `encode` | Whether the value should be hashed using a secure encoding (e.g. for indexing sensitive data). | boolean |

### Example Configuration

```json
{
  "name": "AI Semantic Caching",
  "enabled": true,
  "policy": "ai-semantic-caching",
  "configuration": {
    "modelName": "ai-model-text-embedding-resource",
    "vectorStoreName": "vector-store-redis-resource",
    "promptExpression": "{#jsonPath(#request.content, '$.messages[-1:].content')}",
    "cacheCondition": "{#response.status >= 200}",
    "parameters": [
      {
        "key": "retrieval_context_key",
        "value": "{#context.attributes['api']}",
        "encode": true
      }
    ]
  }
}
```
