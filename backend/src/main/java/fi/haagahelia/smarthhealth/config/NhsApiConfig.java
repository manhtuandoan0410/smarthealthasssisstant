package fi.haagahelia.smarthhealth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NhsApiConfig {

	// Configures the earlier symptom-search API client; condition-name URLs do not use this client.
	@Bean("nhsApiRestClient")
	RestClient nhsApiRestClient(@Value("${nhs.api.base-url:https://api.nhs.uk}") String baseUrl) {
		return RestClient.create(baseUrl);
	}
}
