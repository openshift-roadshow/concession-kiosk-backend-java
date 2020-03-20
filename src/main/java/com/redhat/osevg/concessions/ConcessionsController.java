package com.redhat.osevg.concessions;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class ConcessionsController {

    // These variables are introduced by the mongo secret, for example, linked via odo
    // If they aren't set, no attempt will be made to connect to the DB
    // Yes, they are lowercase in the secret :)
    private static final String ENV_DB_URI = "uri";
    private static final String ENV_DB_USERNAME = "username";
    private static final String ENV_DB_PASSWORD = "password";
    private static final String ENV_DB_NAME = "database_name";

    private MongoClient client;
    private String databaseName;

    private Logger logger = LoggerFactory.getLogger(ConcessionsController.class);

    @GetMapping("/allorders")
    public List allOrders() {
        MongoCollection collection = orders();

        List allOrders = new ArrayList();

        MongoCursor<Document> cursor = collection.find().iterator();
        while (cursor.hasNext()) {
            Document d = cursor.next();
            d.remove("_id");
            allOrders.add(d);
        }
        return allOrders;
    }

    @GetMapping("/ticketNumber")
    public Map ticketNumber(@RequestParam Map<String, String> order) {
        MongoCollection collection = orders();

        // The JS backend started ticket numbers at 100 and incremented from there
        long nextTicket = collection.countDocuments() + 100;

        // Add the order to the database
        Document document = new Document()
            .append("ticketNumber", nextTicket)
            .append("order", order);
        collection.insertOne(document);

        // Package the response
        Map result = new HashMap();
        result.put("success", true);
        result.put("result", nextTicket);
        result.put("order", order);

        return result;
    }

    @GetMapping("/debug")
    public Map debug() {
        boolean connected = false;

        try {
            MongoClient mongoClient = mongoClient();
            mongoClient.listDatabaseNames();
            connected = true;
        } catch (MongoTimeoutException e) {
            // Expected if the DB isn't found
            logger.info("Timeout trying to connect to database");
        }

        Map results = new HashMap();
        results.put("mongo_database", databaseName);
        results.put("connected", connected);

        return results;
    }

    private MongoCollection orders() {
        MongoClient mongoClient = mongoClient();
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return database.getCollection("orders");
    }

    private MongoClient mongoClient() {

        // Lazy loading
        if (client != null) {
            return client;
        }

        // Simple check; assume the rest of the variables are set if the URI is
        String envUri = System.getenv(ENV_DB_URI);
        if (envUri != null) {

            // Example uri value: mongodb://172.30.237.184:27017
            String hostAndPort = envUri.split("//")[1];
            String host = hostAndPort.split(":")[0];
            int port = Integer.parseInt(hostAndPort.split(":")[1]);
            String username = System.getenv(ENV_DB_USERNAME);
            String password = System.getenv(ENV_DB_PASSWORD);
            databaseName = System.getenv(ENV_DB_NAME);

            logger.info("Host: {}", host);
            logger.info("Port: {}", port);
            logger.info("Username: {}", username);
            logger.info("Password: {}", password);
            logger.info("Database: {}", databaseName);

            MongoCredential credentials =  MongoCredential.createCredential(
                    username,
                    databaseName,
                    password.toCharArray());

            client = MongoClients.create(
                    MongoClientSettings.builder()
                            .applyToClusterSettings(builder ->
                                    builder.hosts(Arrays.asList(new ServerAddress(host, port))))
                            .credential(credentials)
                            .build());
        }
        else {
            // If the environment variables aren't included, default to a simple local connection
            client = MongoClients.create("mongodb://localhost:27017");
            databaseName = "concessions";
        }

        return client;
    }
}
