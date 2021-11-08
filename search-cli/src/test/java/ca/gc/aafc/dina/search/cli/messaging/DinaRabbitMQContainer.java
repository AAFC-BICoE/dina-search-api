package ca.gc.aafc.dina.search.cli.messaging;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

public class DinaRabbitMQContainer extends RabbitMQContainer {

  private static final DockerImageName myImage = DockerImageName.parse("rabbitmq:3.8.20-management-alpine");

  public DinaRabbitMQContainer() {
    super(myImage);

    this.addFixedExposedPort(5672, 5672);
    this.addFixedExposedPort(15672, 15672);
  } 
}
