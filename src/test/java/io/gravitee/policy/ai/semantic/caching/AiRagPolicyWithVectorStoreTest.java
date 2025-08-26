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
package io.gravitee.policy.ai.semantic.caching;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.inference.service.InferenceService;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.plugin.resource.ResourcePlugin;
import io.gravitee.policy.ai.rag.AiRagPolicy;
import io.gravitee.policy.ai.rag.configuration.AiRagPolicyConfiguration;
import io.gravitee.policy.ai.rag.resource.AiTestVectorStoreResource;
import io.gravitee.policy.ai.rag.resource.FakeAiModelTextEmbeddingResourcePlugin;
import io.gravitee.policy.ai.rag.resource.FakeAiVectorStoreResourcePlugin;
import io.gravitee.policy.assigncontent.AssignContentPolicy;
import io.gravitee.policy.assigncontent.configuration.AssignContentPolicyConfiguration;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.*;
import org.testcontainers.utility.DockerImageName;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
public class AiRagPolicyWithVectorStoreTest
  extends AbstractPolicyTest<AiRagPolicy, AiRagPolicyConfiguration> {

  private static InferenceService inferenceService;

  @BeforeAll
  public static void setUp() {}

  @Override
  public void loadPolicy(
    PluginManifest manifest,
    Map<String, PolicyPlugin> policies
  ) {
    super.loadPolicy(manifest, policies);
    policies.put(
      AssignContentPolicy.PLUGIN_ID,
      PolicyBuilder.build(
        "policy-assign-content",
        AssignContentPolicy.class,
        AssignContentPolicyConfiguration.class
      )
    );
  }

  @Override
  @SneakyThrows
  public void configureResources(Map<String, ResourcePlugin> resources) {
    inferenceService =
      new InferenceService(
        getBean(Vertx.class),
        Files.currentFolder().getAbsolutePath() + "/models"
      );
    inferenceService.start();

    resources.put(
      "ai-model-text-embedding",
      new FakeAiModelTextEmbeddingResourcePlugin()
    );
    resources.put(
      "ai-vector-store-redis",
      new FakeAiVectorStoreResourcePlugin()
    );
  }

  @Test
  @DisplayName("Must retrieve similar elements from the request")
  @DeployApi("/apis/redis/api-configuration-default.json")
  void must_cache_request_and_return_the_same_response(HttpClient client)
    throws InterruptedException {
    final String input = loadResource(
      "/io/gravitee/policy/ai/semantic/caching/input.json"
    );
    final String llmResponse = loadResource(
      "/io/gravitee/policy/ai/semantic/caching/llm-output.json"
    );

    wiremock.stubFor(
      post("/openai/v1/chat/completions").willReturn(ok(llmResponse))
    );

    Flowable
      .timer(5, TimeUnit.SECONDS)
      .flatMap(__ -> requestToLlm(client, input).map(Buffer::toString))
      .test()
      .await()
      .assertComplete()
      .assertNoErrors();

    assertThat(AiTestVectorStoreResource.INSTANCE.foundRelevant).isTrue();
    //TODO: find a way to verify the content has been put in the context.attributes
  }

  private static Flowable<Buffer> requestToLlm(
    HttpClient client,
    String input
  ) {
    return client
      .rxRequest(POST, "/openai/v1/chat/completions")
      .flatMap(request -> request.rxSend(Buffer.buffer(input)))
      .flatMapPublisher(response -> {
        assertThat(response.statusCode()).isEqualTo(200);
        return response.toFlowable();
      });
  }

  protected String loadResource(String resource) {
    try (InputStream is = this.getClass().getResourceAsStream(resource)) {
      return new String(
        Objects.requireNonNull(is).readAllBytes(),
        StandardCharsets.UTF_8
      );
    } catch (Exception e) {
      return null;
    }
  }

  @AfterEach
  public void afterEach() {
    wiremock.resetAll();
  }

  @AfterAll
  @SneakyThrows
  public static void cleanup() {
    if (inferenceService != null) {
      inferenceService.stop();
    }
  }
}
