package io.intelliflow;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class KubeScheduledMonitor {
	
	@Inject
	KubernetesClient kubernetesClient;

//	@Inject
//	@Channel("kube-out")
//	Emitter<KubeEvent> kubeDetailsEmitter;
	
	//@Scheduled(every = "15s" , delay = 30 , delayUnit = TimeUnit.SECONDS)
	void monitor() {
		List<Service> services = kubernetesClient.services().list().getItems();
		for (Service service : services) {
			Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
			String serviceName = service.getMetadata().getName();
			
			try {

				//kubeDetailsEmitter.send(new KubeEvent(serviceName, nodePort));
			}catch(Exception exception) {
				exception.printStackTrace();
			}
			
			
			
			System.out.println("Service Name found : " + serviceName);
			System.out.println("NodePort found : " + nodePort);
		}
		System.out.println("Tick...............	");

	}
	
	
	

}
