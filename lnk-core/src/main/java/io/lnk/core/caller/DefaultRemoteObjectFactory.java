package io.lnk.core.caller;

import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanClassLoaderAware;

import io.lnk.api.RemoteObject;
import io.lnk.api.RemoteObjectFactory;
import io.lnk.api.RemoteStub;
import io.lnk.api.annotation.LnkService;
import io.lnk.api.protocol.ProtocolFactorySelector;
import io.lnk.api.protocol.object.ObjectProtocolFactory;
import io.lnk.core.LnkEndpoint;

/**
 * @author 刘飞 E-mail:liufei_it@126.com
 *
 * @version 1.0.0
 * @since 2017年5月23日 下午1:33:46
 */
@SuppressWarnings("unchecked")
public class DefaultRemoteObjectFactory implements RemoteObjectFactory, BeanClassLoaderAware {
    private LnkEndpoint endpoint;
    private ConcurrentHashMap<String, Object> remoteObjects = new ConcurrentHashMap<String, Object>();
    private ProtocolFactorySelector protocolFactorySelector;
    private ObjectProtocolFactory objectProtocolFactory;
    private ClassLoader classLoader;

    @Override
    public <T> T getRemoteStub(Class<T> serviceInterface, String serializeStub) {
        Object remoteObject = remoteObjects.get(serializeStub);
        if (remoteObject != null) {
            return serviceInterface.cast(remoteObject);
        }
        RemoteCaller caller = new RemoteCaller(endpoint, serializeStub, protocolFactorySelector);
        caller.setRemoteObjectFactory(this);
        caller.setObjectProtocolFactory(this.objectProtocolFactory);
        remoteObject = Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class[] {serviceInterface, RemoteObject.class, RemoteObjectFactory.class}, caller);
        remoteObjects.put(serializeStub, remoteObject);
        return serviceInterface.cast(remoteObject);
    }

    @Override
    public <T> T getServiceObject(Class<T> serviceInterface, String version) {
        if (serviceInterface.isAnnotationPresent(LnkService.class) == false) {
            throw new RuntimeException(serviceInterface.getName() + " must Annotation by @LnkService.");
        }
        LnkService lnkService = serviceInterface.getAnnotation(LnkService.class);
        RemoteStub remoteStub = new RemoteStub();
        remoteStub.setProtocol(lnkService.protocol());
        remoteStub.setServiceGroup(lnkService.group());
        remoteStub.setServiceId(serviceInterface.getName());
        remoteStub.setVersion(version);
        String serializeStub = remoteStub.serializeStub();
        Object remoteObject = remoteObjects.get(serializeStub);
        if (remoteObject != null) {
            return serviceInterface.cast(remoteObject);
        }
        RemoteCaller caller = new RemoteCaller(endpoint, remoteStub, protocolFactorySelector);
        caller.setRemoteObjectFactory(this);
        caller.setObjectProtocolFactory(objectProtocolFactory);
        remoteObject = Proxy.newProxyInstance(this.classLoader, new Class[] {serviceInterface, RemoteObject.class, RemoteObjectFactory.class}, caller);
        remoteObjects.put(serializeStub, remoteObject);
        return serviceInterface.cast(remoteObject);
    }

    @Override
    public <T> T getServiceObject(String serializeStub) {
        Object remoteObject = remoteObjects.get(serializeStub);
        if (remoteObject != null) {
            return (T) (remoteObject);
        }
        RemoteStub remoteStub = new RemoteStub(serializeStub);
        try {
            Class<T> serviceInterface = (Class<T>) this.classLoader.loadClass(remoteStub.getServiceId());
            RemoteCaller caller = new RemoteCaller(endpoint, remoteStub, protocolFactorySelector);
            caller.setRemoteObjectFactory(this);
            caller.setObjectProtocolFactory(objectProtocolFactory);
            remoteObject = Proxy.newProxyInstance(this.classLoader, new Class[] {serviceInterface, RemoteObject.class, RemoteObjectFactory.class}, caller);
            remoteObjects.put(serializeStub, remoteObject);
            return serviceInterface.cast(remoteObject);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("can't load class serviceId : " + remoteStub.getServiceId() + " class.");
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void setEndpoint(LnkEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void setProtocolFactorySelector(ProtocolFactorySelector protocolFactorySelector) {
        this.protocolFactorySelector = protocolFactorySelector;
    }
    
    public void setObjectProtocolFactory(ObjectProtocolFactory objectProtocolFactory) {
        this.objectProtocolFactory = objectProtocolFactory;
    }
}
