package org.kylin.processor.service;

import org.kylin.address.Address;
import org.kylin.address.AddressNotFoundException;
import org.kylin.common.util.ReflectUtils;
import org.kylin.common.util.RequestCtxUtil;
import org.kylin.protocol.message.Mid;
import org.kylin.protocol.message.Request;
import org.kylin.protocol.message.Response;
import org.kylin.protocol.message.StatusCode;
import org.kylin.serialize.SerializeFactory;
import org.kylin.trace.ResultCode;
import org.kylin.trace.Trace;
import org.kylin.transport.Client;
import org.kylin.transport.ClientFactoryProvider;
import org.kylin.transport.TransportFuture;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Created by jimmey on 15-6-22.
 */
public class ServiceProxy implements InvocationHandler {

    String serviceKey;
    ServiceDiscovery serviceDiscovery;
    static final Object[] EMPTY = new Object[0];

    public ServiceProxy(String service, String version) {
        this.serviceKey = ServiceFactory.getServiceKey(service, version);
        this.serviceDiscovery = new ServiceDiscovery(serviceKey);
    }

    @Override
    public Object invoke(Object proxy, final Method method, Object[] args) throws Throwable {
        Trace.startRpc();
        ResultCode code = ResultCode.OK;
        try {
            String target = RequestCtxUtil.getTargetServerIp();
            Client client = null;
            if (target != null) {
                client = ClientFactoryProvider.get().create(Address.parse(target));
            }
            if (client == null) {
                client = serviceDiscovery.get();
            }
            if (client == null) {
                throw new KylinException(StatusCode.ADDRESS_NOT_FOUND.code, StatusCode.ADDRESS_NOT_FOUND.message);
            }

            Request request = new Request(client.address().getSerializeType());
            request.setMid(Mid.next());
            request.setServiceKey(serviceKey);
            request.setMethod(method.getName());
            request.setArgTypes(ReflectUtils.getArgTypes(method.getParameterTypes()));
            if (args == null) {
                args = EMPTY;
            }
            request.setArgs(args);
            request.putContext("invoke-time", System.currentTimeMillis() + "");
            TransportFuture future = TransportFuture.create(request);
            client.doAsk(future);
            Response response = future.get();
            if (response.getStatus() != StatusCode.OK.code) {
                throw new KylinException(response.getStatus(), response.getException());
            }
            byte[] result = response.getResultBytes();
            if (result != null && result.length > 0) {
                return SerializeFactory.deserialize(response.getSerializeType(), result, method.getReturnType());
            }
            return null;
        } catch (Throwable e) {
            code = ResultCode.ERROR;
            throw e;
        } finally {
            Trace.end(code);
//            System.out.println("use " + (System.currentTimeMillis() - start));
        }
    }

}
