package kz.dentify.platform.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Соглашения {@code /api/v1} в Spring MVC: параметр {@code PageQuery} в методах контроллеров. */
@Configuration(proxyBeanMethods = false)
class ApiWebConfiguration implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new PageQueryArgumentResolver());
	}

}
