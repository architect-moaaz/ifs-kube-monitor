package io.intelliflow;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@QuarkusMain
public class KubernetesControllerApplication implements QuarkusApplication {

    @Inject
    SharedInformerFactory sharedInformerFactory;
    @Inject
    ResourceEventHandler<Pod> serviceEventHandler;

    @Override
    public int run(String... args) throws Exception {
        sharedInformerFactory.startAllRegisteredInformers().get();
        final var serviceHandler = sharedInformerFactory.getExistingSharedIndexInformer(Pod.class);
        serviceHandler.addEventHandler(serviceEventHandler);
        Quarkus.waitForExit();
        return 0;
    }

    void onShutDown(@Observes ShutdownEvent event) {
        sharedInformerFactory.stopAllRegisteredInformers(true);
    }

    public static void main(String... args) {
        Quarkus.run(KubernetesControllerApplication.class, args);
    }

    @ApplicationScoped
    static final class KubernetesControllerApplicationConfig {

        @Inject
        @ConfigProperty(name = "namespaceToBeAvoidedList")
        List<String> nameSpacesToBeAvoided;

        @Inject
        @Channel("kube-out")
        Emitter<KubeEvent> kubeDetailsEmitter;

        @Inject
        KubernetesClient client;

        @Singleton
        SharedInformerFactory sharedInformerFactory() {
            return client.informers();
        }

        @Singleton
        SharedIndexInformer<Pod> serviceInformer(SharedInformerFactory factory) {
            return factory.sharedIndexInformerFor(Pod.class, 0);
        }

        @Singleton
        ResourceEventHandler<Pod> serviceReconciler(SharedIndexInformer<Pod> serviceInformer) {
            return new ResourceEventHandler<>() {
                @Override
                public void onAdd(Pod pod) {
                    String namespace = pod.getMetadata().getNamespace();
                    if (pod.getMetadata().getLabels() != null) {
                        String podName = pod.getMetadata().getLabels().get("app.kubernetes.io/name");
                        if (podName != null) {
                            Service service = client.services().inNamespace(namespace).withName(podName).get();
                            if (service != null) {
                                Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
                                String serviceName = service.getMetadata().getName();
                                if (!nameSpacesToBeAvoided.contains(service.getMetadata().getNamespace())) {
                                    try {
                                        kubeDetailsEmitter.send(new KubeEvent(serviceName, nodePort));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    Log.info("The service: " + serviceName + " is exposed on port: " + nodePort);
                                } else {
                                    Log.error("The pod: "+ podName+" is not a miniapp");
                                }
                            } else {
                                Log.error("There is no Service for the pod: " + podName + " In the namespace: " + namespace);
                            }
                        }
                    } else {
                        Log.error("podName is null for the pod in namespace: " + namespace);
                    }
                }

                @Override
                public void onUpdate(Pod service, Pod t1) {
                    //Can be used later...
                }

                @Override
                public void onDelete(Pod service, boolean b) {
                    //Can be used later...
                }
            };
        }
    }
}