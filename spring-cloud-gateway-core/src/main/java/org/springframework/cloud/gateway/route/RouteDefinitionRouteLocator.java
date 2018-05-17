/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;

/**
 * 路由定位器
 *  此实现通过路由定义（RouteDefinition）转换路由（Route）
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}
 * @author Spencer Gibb
 */
public class RouteDefinitionRouteLocator implements RouteLocator, BeanFactoryAware, ApplicationEventPublisherAware {
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 路由定位定位器
	 */
	private final RouteDefinitionLocator routeDefinitionLocator;

	/**
	 * 路由的Predicate 创建工厂MAP
	 * key - RoutePredicateFactory name
	 * value - RoutePredicateFactory
	 */
	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();

	/**
	 * 路由的过滤器创建工厂MAP
	 * key - FilterFactory name
	 * value - GatewayFilterFactory
	 */
	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();
	/**
	 * 网关配置信息
	 */
	private final GatewayProperties gatewayProperties;
	/**
	 *
	 */
	private final SpelExpressionParser parser = new SpelExpressionParser();
	/**
	 * BeanFactory
	 */
	private BeanFactory beanFactory;
	/**
	 * 事件发布
	 */
	private ApplicationEventPublisher publisher;

	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
									   List<RoutePredicateFactory> predicates,
									   List<GatewayFilterFactory> gatewayFilterFactories,
									   GatewayProperties gatewayProperties) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		initFactories(predicates);
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));
		this.gatewayProperties = gatewayProperties;
	}

	@Autowired
	private Validator validator;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();
			if (this.predicates.containsKey(key)) {
				this.logger.warn("A RoutePredicateFactory named "+ key
						+ " already exists, class: " + this.predicates.get(key)
						+ ". It will be overwritten.");
			}
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	@Override
	public Flux<Route> getRoutes() {
		//获取到所有的RouteDefinition
		return this.routeDefinitionLocator.getRouteDefinitions()
				//遍历转换成对应的Route信息
				.map(this::convertToRoute)
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					return route;
				});


		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/**
	 * RouteDefinition 转换为对应的Route
	 * @param routeDefinition
	 * @return
	 */
	private Route convertToRoute(RouteDefinition routeDefinition) {
		//获取routeDefinition中的Predicate信息
		Predicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);
		//获取routeDefinition中的GatewayFilter信息
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);
		//构建路由信息
		return Route.builder(routeDefinition)
				.predicate(predicate)
				.replaceFilters(gatewayFilters)
				.build();
	}

	/**
	 * 加载过滤器，根据过滤器的定义加载
	 * @param id
	 * @param filterDefinitions
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		//遍历过滤器定义，将过滤器定义转换成对应的过滤器
		List<GatewayFilter> filters = filterDefinitions.stream()
				.map(definition -> {
					//通过过滤器定义名称获取过滤器创建工厂
					GatewayFilterFactory factory = this.gatewayFilterFactories.get(definition.getName());
					if (factory == null) {
                        throw new IllegalArgumentException("Unable to find GatewayFilterFactory with name " + definition.getName());
					}
					//获取参数
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition " + id + " applying filter " + args + " to " + definition.getName());
					}

					//根据args组装配置信息
                    Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);
					//构建过滤器创建配置信息
                    Object configuration = factory.newConfig();
                    ConfigurationUtils.bind(configuration, properties,
                            factory.shortcutFieldPrefix(), definition.getName(), validator);

                    //通过过滤器工厂创建GatewayFilter
                    GatewayFilter gatewayFilter = factory.apply(configuration);
                    if (this.publisher != null) {
                    	//发布事件
                        this.publisher.publishEvent(new FilterArgsEvent(this, id, properties));
                    }
                    return gatewayFilter;
				})
				.collect(Collectors.toList());

		ArrayList<GatewayFilter> ordered = new ArrayList<>(filters.size());
		//包装过滤器使其所有过滤器继承Ordered属性，可进行排序
		for (int i = 0; i < filters.size(); i++) {
			GatewayFilter gatewayFilter = filters.get(i);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

	/**
	 * 获取RouteDefinition中的过滤器集合
	 * @param routeDefinition
	 * @return
	 */
	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();

		//校验gatewayProperties是否含义默认的过滤器集合
		//TODO: support option to apply defaults after route specific filters?
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			//加载全局配置的默认过滤器集合
			filters.addAll(loadGatewayFilters("defaultFilters",
					this.gatewayProperties.getDefaultFilters()));
		}

		if (!routeDefinition.getFilters().isEmpty()) {
			//加载路由定义中的过滤器集合
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), routeDefinition.getFilters()));
		}

		//排序
		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	/**
	 * 返回组合的谓词
	 * @param routeDefinition
	 * @return
	 */
	private Predicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {
		//获取RouteDefinition中的PredicateDefinition集合
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();

		Predicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			Predicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);
			//返回一个组合的谓词，表示该谓词与另一个谓词的短路逻辑AND
			predicate = predicate.and(found);
		}

		return predicate;
	}

	/**
	 * 获取一个谓语定义（PredicateDefinition）转换的谓语
	 * @param route
	 * @param predicate
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Predicate<ServerWebExchange> lookup(RouteDefinition route, PredicateDefinition predicate) {
		//获取谓语创建工厂
		RoutePredicateFactory<Object> factory = this.predicates.get(predicate.getName());
		if (factory == null) {
            throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}
		//获取参数
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + route.getId() + " applying "
					+ args + " to " + predicate.getName());
		}

		//组装参数
        Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);
        //构建创建谓语的配置信息
		Object config = factory.newConfig();
        ConfigurationUtils.bind(config, properties,
                factory.shortcutFieldPrefix(), predicate.getName(), validator);
        if (this.publisher != null) {
            this.publisher.publishEvent(new PredicateArgsEvent(this, route.getId(), properties));
        }
        //通过谓语工厂构建谓语
        return factory.apply(config);
	}
}
