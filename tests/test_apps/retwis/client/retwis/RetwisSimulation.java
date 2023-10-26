package retwis;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.VoltTable;

public class RetwisSimulation {
    private int next_u_id;
    private int next_post_id;
    private boolean async;

    public class ProcCaller {
    }

    final ClientConnection client;
    private static Random rnd = new Random();
    
    public RetwisSimulation(ClientConnection client, boolean async) {
        this.client = client;
        this.next_post_id = 0;
        this.next_u_id = 0;
        this.async = async;
    }

    private VoltTable[] callProcedure(Benchmark.RetwisCallback cb, String procedure, Object... parameters) throws Exception {
        cb.setProcedure(procedure);
        VoltTable[] results = null;
        if (this.async)
            this.client.executeAsync(cb, procedure, parameters);
        else {
            ClientResponse response = this.client.execute(procedure, parameters);
            cb.clientCallback(response);
            results = response.getResults();
        }
        return results;
    }

    private void doCreateUser(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = this.next_u_id;
        this.next_u_id += 1;
        String username = "user" + rnd.nextInt();
        try {
            this.callProcedure(cb, "CreateUser", u_id, username);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doPost(Benchmark.RetwisCallback cb) throws IOException {
        int post_id = this.next_post_id;
        this.next_post_id += 1;
        int u_id = rnd.nextInt(this.next_u_id);
        String post = "This is a ReTweet!";
        try {
            this.callProcedure(cb, "Post", u_id, post_id, post);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doGetTimeline(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.callProcedure(cb, "GetTimeline", u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void doGetPosts(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.callProcedure(cb, "GetPosts", u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doFollow(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        int follower_u_id = rnd.nextInt(this.next_u_id);
        try {
            this.callProcedure(cb, "Follow", u_id, follower_u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doGetFollowers(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.callProcedure(cb, "GetFollowers", u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void doOne(Benchmark.RetwisCallback cb) throws IOException {
        int n = rnd.nextInt(101);
        if (this.next_u_id < 50 || n < 5) {
            doCreateUser(cb);
        } else if (n <= 65) { // 60%
            doGetTimeline(cb);
        } else if (n <= 65 + 15) { // 15%
            doGetPosts(cb);
        } else if (n <= 65 + 15 + 5) { // 5%
            doGetFollowers(cb);
        } else if (n <= 65 + 15 + 5 + 10) { // 10%
            doPost(cb);
        } else { // 5%
            assert n > 100 - 90;
            doFollow(cb);
        }
    }

    public void doWarmupOne(Benchmark.RetwisCallback cb) throws IOException {
        int n = rnd.nextInt(101);
        if (this.next_u_id < 50 || n < 3) {
            doCreateUser(cb);
        } else if (n <= 3 + 90) {
            doPost(cb);
        } else {
            doFollow(cb);
        }
    }
}
