/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.ai.rag;

import static com.google.common.hash.Hashing.murmur3_128;
import static io.gravitee.gateway.reactive.api.context.ContextAttributes.ATTR_PREFIX;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.ai.rag.configuration.AiRagPolicyConfiguration;
import io.gravitee.policy.ai.rag.configuration.Parameter;
import io.gravitee.policy.ai.rag.resource.ResourceProvider;
import io.gravitee.resource.ai.vector.store.api.VectorEntity;
import io.gravitee.resource.ai.vector.store.api.VectorResult;
import io.gravitee.resource.ai_model.api.model.PromptInput;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collector;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AiRagPolicy implements HttpPolicy {

  public static final String AI_RAG = "ai-retrieval-augmented-generation";
  private static final Collector<Entry<String, Object>, ?, Map<String, Object>> MAP_COLLECTOR =
    toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v2);

  private final ResourceProvider resourceProvider;

  private final List<Parameter> parameters;
  private final String promptExpression;
  private final String promptTemplate;
  private final String resultsAttribute;

  public AiRagPolicy(AiRagPolicyConfiguration config) {
    this.parameters =
      config.parameters() == null ? List.of() : config.parameters();
    this.resultsAttribute = config.resultsAttribute();
    this.promptExpression = config.promptExpression();
    this.promptTemplate = config.promptTemplate();
    this.resourceProvider = new ResourceProvider(config);
  }

  @Override
  public String id() {
    return AI_RAG;
  }

  @Override
  public Completable onRequest(HttpPlainExecutionContext ctx) {
    return getRequest(ctx);
  }

  @Override
  public Completable onMessageRequest(HttpMessageExecutionContext ctx) {
    return getRequest(ctx);
  }

  private Completable getRequest(HttpBaseExecutionContext ctx) {
    return findRelevant(ctx)
      .concatMapCompletable(results -> this.storeResults(ctx, results))
      .doOnError(t ->
        log.error(
          "Could not perform retrieval augmented generation: {}",
          t.getMessage(),
          t
        )
      )
      .onErrorComplete();
  }

  private Completable storeResults(
    HttpBaseExecutionContext ctx,
    List<VectorResult> results
  ) {
    var collect = results
      .stream()
      .map(vr -> vr.entity().metadata())
      .collect(toList());
    ctx.putAttribute(resultsAttribute, collect);

    return getExpression(ctx, this.promptTemplate)
      .concatMapCompletable(prompt -> {
        ctx.putAttribute(
          this.resultsAttribute + "." + "prompt",
          Json.encode(prompt)
        );
        return Completable.complete();
      });
  }

  private Maybe<List<VectorResult>> findRelevant(HttpBaseExecutionContext ctx) {
    return getExpression(ctx, this.promptExpression)
      .map(PromptInput::new)
      .flatMapSingle(content -> this.findRelevant(ctx, content));
  }

  private Single<List<VectorResult>> findRelevant(
    HttpBaseExecutionContext ctx,
    PromptInput content
  ) {
    return resourceProvider
      .aiTextEmbeddingModel(ctx)
      .invokeModel(content)
      .flatMap(embeddingResult ->
        getMetadata(ctx.getTemplateEngine())
          .flatMap(metadata ->
            resourceProvider
              .vectorStore(ctx)
              .findRelevant(
                new VectorEntity(
                  content.promptContent(),
                  embeddingResult.embeddings(),
                  metadata
                )
              )
              .toList()
          )
      );
  }

  private Single<Map<String, Object>> getMetadata(
    TemplateEngine templateEngine
  ) {
    return Flowable
      .fromIterable(parameters)
      .flatMapMaybe(parameter ->
        templateEngine
          .eval(parameter.value(), Object.class)
          .map(value ->
            parameter.encode() ? encodeValue(String.valueOf(value)) : value
          )
          .map(value -> Map.entry(parameter.key(), value))
      )
      .toList()
      .map(entries -> entries.stream().collect(MAP_COLLECTOR));
  }

  private static Object encodeValue(String value) {
    return Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(
        murmur3_128().hashString(value, StandardCharsets.UTF_8).asBytes()
      );
  }

  private Maybe<String> getExpression(
    HttpBaseExecutionContext ctx,
    String expression
  ) {
    return ctx.getTemplateEngine().eval(expression, String.class);
  }
}
