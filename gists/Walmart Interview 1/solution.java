/*
Test Case:

7
HIT 1 trip alice
HIT 2 trip alice
HIT 60 trip bob
COUNT 60 GROUP trip
COUNT 61 GROUP trip
COUNT 61 GROUP trip BREAKDOWN user
COUNT 62

{"group":"trip","total":2}
{"group":"trip","total":1}
{"group":"trip","window":"last_60_seconds","totals":{"bob":1}}
{"total":1}

{"group":"trip","total":3}
{"group":"trip","total":2}
{"group":"trip","window":"last_60_seconds","totals":{"alice":1,"bob":1}}
{"total":1}

---------------

*/

import java.io.*;
import java.util.*;

/**
 * Rolling Hit Counter (60-second window)
 *
 * Rolling window rule for query at time T: include timestamps in [T-59, T] inclusive.
 *
 * This solution supports:
 * 1) hit <ts> <group> [user]
 * 2) total <ts>
 * 3) group <ts> <group>
 * 4) users <ts> <group>
 *
 * Reads commands from STDIN, prints results to STDOUT.
 *
 * Notes:
 * - Evicts expired hits on every command using the command's timestamp.
 * - Stores only hits within last 60 seconds.
 */
public class Main {

    // Represents one recorded hit
    static class Hit {
        final int ts;
        final String group;
        final String user; // can be null

        Hit(int ts, String group, String user) {
            this.ts = ts;
            this.group = group;
            this.user = user;
        }
    }

    static class RollingHitCounter {
        private final Deque<Hit> q = new ArrayDeque<>();
        private long total = 0;

        private final Map<String, Long> groupCounts = new HashMap<>();
        private final Map<String, Map<String, Long>> groupUserCounts = new HashMap<>();

        // Evict hits older than (now - 59)
        private void evict(int now) {
            int minTs = now - 59;
            while (!q.isEmpty() && q.peekFirst().ts < minTs) {
                Hit old = q.removeFirst();

                total--;

                // group counts
                Long gc = groupCounts.get(old.group);
                if (gc != null) {
                    if (gc == 1) groupCounts.remove(old.group);
                    else groupCounts.put(old.group, gc - 1);
                }

                // per-user counts (only if user provided)
                if (old.user != null) {
                    Map<String, Long> ucMap = groupUserCounts.get(old.group);
                    if (ucMap != null) {
                        Long uc = ucMap.get(old.user);
                        if (uc != null) {
                            if (uc == 1) ucMap.remove(old.user);
                            else ucMap.put(old.user, uc - 1);
                        }
                        if (ucMap.isEmpty()) groupUserCounts.remove(old.group);
                    }
                }
            }
        }

        public void recordHit(int ts, String group, String user) {
            evict(ts);

            q.addLast(new Hit(ts, group, user));
            total++;

            groupCounts.put(group, groupCounts.getOrDefault(group, 0L) + 1);

            if (user != null) {
                Map<String, Long> ucMap = groupUserCounts.computeIfAbsent(group, k -> new HashMap<>());
                ucMap.put(user, ucMap.getOrDefault(user, 0L) + 1);
            }
        }

        public long queryTotal(int ts) {
            evict(ts);
            return total;
        }

        public long queryGroup(int ts, String group) {
            evict(ts);
            return groupCounts.getOrDefault(group, 0L);
        }

        /**
         * Returns per-user breakdown for a group at timestamp ts.
         * If no users recorded for the group in window, returns empty map.
         */
        public Map<String, Long> queryUsers(int ts, String group) {
            evict(ts);
            Map<String, Long> m = groupUserCounts.get(group);
            if (m == null) return Collections.emptyMap();

            // Return a stable sorted copy (helps deterministic tests)
            TreeMap<String, Long> sorted = new TreeMap<>(m);
            return sorted;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static void main(String[] args) throws Exception {
        RollingHitCounter counter = new RollingHitCounter();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (isBlank(line)) continue;

            // Split on whitespace
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            switch (cmd) {
                // hit <ts> <group> [user]
                case "hit": {
                    if (parts.length < 3) break; // or throw
                    int ts = Integer.parseInt(parts[1]);
                    String group = parts[2];
                    String user = (parts.length >= 4) ? parts[3] : null;
                    counter.recordHit(ts, group, user);
                    break;
                }

                // total <ts>
                case "total": {
                    if (parts.length < 2) break;
                    int ts = Integer.parseInt(parts[1]);
                    long ans = counter.queryTotal(ts);
                    System.out.println(ans);
                    break;
                }

                // group <ts> <group>
                case "group": {
                    if (parts.length < 3) break;
                    int ts = Integer.parseInt(parts[1]);
                    String group = parts[2];
                    long ans = counter.queryGroup(ts, group);
                    System.out.println(ans);
                    break;
                }

                // users <ts> <group>
                case "users": {
                    if (parts.length < 3) break;
                    int ts = Integer.parseInt(parts[1]);
                    String group = parts[2];
                    Map<String, Long> users = counter.queryUsers(ts, group);

                    // Output format: one "user count" per line; if empty, print 0 lines.
                    // If your platform expects something else (e.g., JSON, "user:count", etc.),
                    // adjust here.
                    for (Map.Entry<String, Long> e : users.entrySet()) {
                        System.out.println(e.getKey() + " " + e.getValue());
                    }
                    break;
                }

                default:
                    // Unknown command; ignore or print error.
                    // System.err.println("Unknown command: " + cmd);
                    break;
            }
        }
    }
}