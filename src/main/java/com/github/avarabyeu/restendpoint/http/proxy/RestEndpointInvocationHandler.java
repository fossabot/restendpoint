package com.github.avarabyeu.restendpoint.http.proxy;

import com.github.avarabyeu.restendpoint.http.HttpClientRestEndpoint;
import com.github.avarabyeu.restendpoint.http.Response;
import com.github.avarabyeu.restendpoint.http.RestEndpoint;
import com.google.common.base.Preconditions;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Proxy invocation handler for REST interfaces
 * Reads interface methods and caches all needed information for request execution.
 * Once method call happens, builds {@link com.github.avarabyeu.restendpoint.http.RestCommand}
 * and delegates execution to {@link com.github.avarabyeu.restendpoint.http.RestEndpoint}
 *
 * @author Andrey Vorobyov
 */
public class RestEndpointInvocationHandler implements InvocationHandler {

    private final Map<Method, RestMethodInfo> restMethods;

    private final RestEndpoint delegate;

    public RestEndpointInvocationHandler(Class<?> clazz, RestEndpoint restEndpoint) {
        this.delegate = restEndpoint;
        this.restMethods = RestMethodInfo.mapMethods(clazz);
    }

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return executeRestMethod(method, args);
    }

    private Object executeRestMethod(Method method, Object[] args) throws Throwable {

        Preconditions
                .checkState(restMethods.containsKey(method), "Method with name [%s] is not mapped", method.getName());

        /* find appropriate method information*/
        RestMethodInfo methodInfo = restMethods.get(method);

        /* delegate request execution to RestEndpoint */
        Mono<Response<Object>> response = delegate.executeRequest(methodInfo.createRestCommand(args));

        Mono<?> result = methodInfo.isBodyOnly() ?
                response.then(new HttpClientRestEndpoint.BodyTransformer<>()) :
                response;

        if (methodInfo.isAsynchronous()) {
            return result;
        } else {
            /* cannot block twice so cache it */
            final Mono<?> cached = result.cache();
            return cached.hasElement().block() ? cached.block() : null;
        }
    }

}
