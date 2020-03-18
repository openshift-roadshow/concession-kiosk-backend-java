package com.redhat.osevg.concessions;

import com.mongodb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ConcessionsController {

    private MongoClient client;
    private Logger logger = LoggerFactory.getLogger(ConcessionsController.class);

    @GetMapping("/allorders")
    public List<DBObject> allOrders() {
        DBCollection collection = orders();

        DBCursor allOrders = collection.find();
        return allOrders.toArray();
    }

    @GetMapping("/ticketNumber")
    public Map ticketNumber(@RequestParam Map<String, String> order) {
        DBCollection collection = orders();

        int nextTicket = 100;

        // Increment the ticket number from the last order if one is found
        int count = collection.find().count();
        if (count > 0) {
            DBCursor cursor = collection.find().sort(new BasicDBObject("ticketNumber", -1)).limit(1);
            DBObject lastOrder = cursor.next();
            nextTicket = (int) lastOrder.get("ticketNumber") + 1;
        }

        // Add the order to the database
        BasicDBObject document = new BasicDBObject();
        document.put("ticketNumber", nextTicket);
        document.put("order", order);
        collection.insert(document);

        // Package the response
        Map result = new HashMap();
        result.put("success", true);
        result.put("result", nextTicket);
        result.put("order", order);

        return result;
    }

    @GetMapping("/debug")
    public Map debug() {
        MongoClientURI mongoUri = mongoClientURI();

        boolean connected = false;

        try {
            MongoClient mongoClient = mongoClient();
            mongoClient.getDatabaseNames();
            connected = true;
        } catch (MongoTimeoutException e) {
            // Expected if the DB isn't found
        }

        Map results = new HashMap();
        results.put("mongo_url", mongoUri.getURI());
        results.put("connected", connected);

        return results;
    }

    private DBCollection orders() {
        MongoClientURI mongoUri = mongoClientURI();
        MongoClient mongoClient = mongoClient();
        DB database = mongoClient.getDB(mongoUri.getDatabase());
        return database.getCollection("orders");
    }

    private MongoClient mongoClient() {
        if (client == null) {
            try {
                client = new MongoClient(mongoClientURI());
            } catch (UnknownHostException e) {
                logger.error("Host not found", e);
            }
        }
        return client;
    }

    /**
     * Reads the appropriate environment variables to determine the mongo URI.
     */
    private MongoClientURI mongoClientURI() {

        // Default if it's not overridden from the environment
        String uri = "mongodb://localhost:27017/sampledb";

        // These variables are introduced by the mongo secret, for example, linked via odo
        // Yes, they are lowercase in the secret :)
        String envUri = System.getenv("uri");
        if (envUri != null) {
            String username = System.getenv("username");
            String password = System.getenv("password");
            String dbName = System.getenv("database_name");
            String[] pieces = envUri.split("//");
            uri = pieces[0] + "//" + username + ":" + password + "@" + pieces[1] + "/" + dbName;
        } else if (System.getenv("MONGODB_URL") != null) {
            uri = System.getenv("MONGODB_URL");
        }

        logger.info("Using {}", uri);

        // Set to a low timeout since during the demo there is a case where we deploy this
        // app without having the database, so no need to wait the default 10 seconds
        MongoClientOptions.Builder optionsBuilder = MongoClientOptions.builder();
        optionsBuilder.connectTimeout(2000);

        return new MongoClientURI(uri, optionsBuilder);
    }
}
