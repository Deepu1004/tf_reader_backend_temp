package com.tf.reader.hold;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

// Real Mongo AND real Redis — hold is the one module that needs both at
// once, since position, promotion and availability all live on the Redis
// side while the hold itself lives in Mongo.
//
// Singleton containers, started once in a static initializer rather than
// with @Container/@Testcontainers: that annotation pair stops the containers
// after EVERY test class, and a static field on this shared base class is
// the same field for every subclass — the second IT class to run would get
// a dead container. Ryuk still reaps these at JVM exit.
@SpringBootTest
public abstract class HoldContainerTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        MONGO.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
