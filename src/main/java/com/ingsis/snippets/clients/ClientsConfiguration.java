package com.ingsis.snippets.clients;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.RestLanguageClient;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.RestPermissionClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServicesProperties.class)
public class ClientsConfiguration {

	@Bean
	public LanguageClient languageClient(RestClient.Builder builder, ServicesProperties properties) {
		return new RestLanguageClient(restClient(builder, properties.language(), properties));
	}

	@Bean
	public PermissionClient permissionClient(RestClient.Builder builder, ServicesProperties properties) {
		return new RestPermissionClient(restClient(builder, properties.permission(), properties));
	}

	private static RestClient restClient(RestClient.Builder builder, ServicesProperties.Endpoint endpoint,
			ServicesProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return builder.clone().baseUrl(endpoint.url()).requestFactory(requestFactory).build();
	}

}
