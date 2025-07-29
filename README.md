# AI Retrieval-Augmented Generation (RAG) Policy

[![Available at Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-ee/apim/plugins/policies/gravitee-policy-ai-rag/)[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-ai-rag/blob/master/LICENSE.txt)[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-ai-rag/releases)[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-ai-rag.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-ai-rag)

## Phases

| onRequest | onResponse | onMessageRequest | onMessageResponse |
|:---------:|:----------:|:----------------:|:-----------------:|
|     ✅     |            |                  |                   |

## Description

The `ai-retrieval-augmented-generation` policy enables **Retrieval-Augmented Generation (RAG)**, enriching prompts with context retrieved from a vector store.  
It uses an AI embedding model to convert the incoming request into a vector, then queries a vector store for the most relevant documents.  
The retrieved results are stored in the execution context and injected into a **prompt template**, allowing the language model to generate more accurate and context-aware responses.

This policy integrates with AI resources such as:
- **Text embedding models** (for vector generation)
- **Vector stores** (for similarity search and retrieval)

RAG provides a hybrid approach: dynamic generation with context-aware retrieval.

> ℹ️ This policy is especially useful for **knowledge-base Q&A**, **document search augmentation**, and **API assistants** that require contextual understanding.

## Configuration

You can configure the policy with the following options:

| Property           | Required | Description                                                                                                           | Type   | Default                                                    |
|--------------------|----------|-----------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------|
| `modelName`        | ✅        | The unique identifier of the embedding model resource to use for vector generation.                                   | string | —                                                          |
| `vectorStoreName`  | ✅        | The name of the vector store resource used to retrieve relevant documents.                                            | string | —                                                          |
| `resultsAttribute` | ✅        | The context attribute where retrieved results will be stored for later use in the prompt template.                    | string | `ragResults`                                               |
| `promptExpression` | ✅        | EL expression to extract the user’s query or request content for embedding and similarity search.                     | string | `{#jsonPath(#request.content, '$.messages[-1:].content')}` |
| `promptTemplate`   | ✅        | The template that guides the AI model’s response. Can reference context attributes (e.g., results from retrieval).    | string | See [default example](#default-prompt-template)            |
| `parameters`       |          | List of key-value pairs used as metadata filters in the vector search. Values support EL and can be securely encoded. | array  | —                                                          |

### Parameter Object Structure

Each `parameter` item contains:

| Property | Description                                                                                     | Type    |
|----------|-------------------------------------------------------------------------------------------------|---------|
| `key`    | Name of the metadata field to store or query with.                                              | string  |
| `value`  | EL expression or static string for the metadata value.                                          | string  |
| `encode` | Whether the value should be hashed and encoded for safe indexing (e.g., sensitive information). | boolean |

### Default Prompt Template

```text
Answer this question to the best of your abilities:

    Question: {#jsonPath(#request.content, '$.messages[-1:].content')}

Use the information below to construct your answer:

    Information: {#context.attributes['ragResults'][0]['content']}

If no information was submitted, just answer you do not know.
```

### Example Configuration

```json
{
  "name": "AI RAG Policy",
  "enabled": true,
  "policy": "ai-retrieval-augmented-generation",
  "configuration": {
    "modelName": "ai-model-text-embedding-resource",
    "vectorStoreName": "vector-store-redis-resource",
    "resultsAttribute": "ragResults",
    "promptExpression": "{#jsonPath(#request.content, '$.messages[-1:].content')}",
    "promptTemplate": "Answer this question: {#jsonPath(#request.content, '$.messages[-1:].content')} with context: {#context.attributes['ragResults']}",
    "parameters": [
      {
        "key": "tenant_id",
        "value": "{#context.attributes['tenant']}",
        "encode": true
      }
    ]
  }
}
```

---

## License

This project is licensed under the [Apache License 2.0](./LICENSE).