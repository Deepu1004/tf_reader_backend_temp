package com.tf.reader.hold;

import com.tf.reader.ContainerisedInfrastructure;
import org.springframework.boot.test.context.SpringBootTest;

// Real Mongo AND real Redis — hold is the one module that needs both at
// once, since position, promotion and availability all live on the Redis
// side while the hold itself lives in Mongo. Reuses ContainerisedInfrastructure
// rather than hand-rolling a second copy of the same singleton-container
// pattern — it already gets the property name right (spring.mongodb.uri,
// not spring.data.mongodb.uri, which Spring Boot 4 silently ignores).
@SpringBootTest
public abstract class HoldContainerTest extends ContainerisedInfrastructure {
}
