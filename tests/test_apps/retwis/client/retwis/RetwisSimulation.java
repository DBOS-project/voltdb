package retwis;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;

public class RetwisSimulation {
    private int next_u_id;
    private int next_post_id;

    public class ProcCaller {
    }

    final ClientConnection client;
    private static Random rnd = new Random();
    
    public RetwisSimulation(ClientConnection client) {
        this.client = client;
        this.next_post_id = 0;
        this.next_u_id = 0;
    }

    private void doCreateUser(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = this.next_u_id;
        this.next_u_id += 1;
        String username = "user" + rnd.nextInt();
        try {
            this.client.executeAsync(cb, "CreateUser", u_id, username);
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
            this.client.executeAsync(cb, "Post", post_id, u_id, post);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doGetTimeline(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.client.executeAsync(cb, "GetTimeline", u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doGetPosts(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.client.executeAsync(cb, "GetPosts", u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doFollow(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        int follower_u_id = rnd.nextInt(this.next_u_id);
        try {
            this.client.executeAsync(cb, "Follow", u_id, follower_u_id);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doGetFollowers(Benchmark.RetwisCallback cb) throws IOException {
        int u_id = rnd.nextInt(this.next_u_id);
        try {
            this.client.executeAsync(cb, "GetFollowers", u_id);
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
        } else if (n <= 65 + 10) { // 10%
            doGetPosts(cb);
        } else if (n <= 65 + 10 + 5) { // 5%
            doGetFollowers(cb);
        } else if (n <= 65 + 10 + 5 + 15) { // 15%
            doPost(cb);
        } else { // 5%
            assert n > 100 - 90;
            doFollow(cb);
        }
    }
}
