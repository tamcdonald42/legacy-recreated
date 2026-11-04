import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;

/**
 * Walk the call graph down from WinMain_ and label each reachable function with the
 * subsystem(s) its direct callees belong to (graphics/audio/ui/os/video/assets/...),
 * using the symbol->subsystem map built from the SR-Septerra shim headers.
 *
 * Usage: -postScript CallTree.java [rootHex] [maxDepth]
 * Defaults: root 0x412b80 (WinMain_), depth 6.
 *
 * Output: work/ghidra-out/calltree_nodes.csv, calltree_edges.csv
 */
public class CallTree extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";
    private static final String SYMBOLS = OUT + "shim_symbols.csv";

    private Map<String, String> subOf = new HashMap<>();   // symbol or base -> subsystem

    private static String base(String n) {
        for (String suf : new String[]{"_asm2c", "_c2asm", "_c"}) {
            if (n.endsWith(suf)) return n.substring(0, n.length() - suf.length());
        }
        return n;
    }

    private void loadSymbols() throws Exception {
        File f = new File(SYMBOLS);
        if (!f.exists()) { println("WARN: no symbol map at " + SYMBOLS); return; }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 3) continue;
                subOf.put(p[0].trim(), p[2].trim());
                subOf.put(p[1].trim(), p[2].trim());
            }
        }
        println("loaded " + subOf.size() + " symbol->subsystem mappings");
    }

    private Set<String> subsystemsOf(Function f, Set<Function> callees) {
        Set<String> out = new TreeSet<>();
        for (Function c : callees) {
            String n = c.getName();
            String s = subOf.get(n);
            if (s == null) s = subOf.get(base(n));
            if (s != null) out.add(s);
        }
        return out;
    }

    @Override
    public void run() throws Exception {
        loadSymbols();

        String[] args = getScriptArgs();
        long rootAddr = (args.length > 0) ? Long.parseLong(args[0].replace("0x", ""), 16) : 0x412b80L;
        int maxDepth = (args.length > 1) ? Integer.parseInt(args[1]) : 6;

        Address ra = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(rootAddr);
        FunctionManager fm = currentProgram.getFunctionManager();
        Function root = fm.getFunctionAt(ra);
        if (root == null) { println("ERROR: no function at " + ra); return; }
        println("root: " + root.getName() + " @ " + ra + " depth<=" + maxDepth);

        Map<Function, Integer> depthOf = new LinkedHashMap<>();
        Map<Function, Set<Function>> callees = new LinkedHashMap<>();
        ArrayDeque<Function> queue = new ArrayDeque<>();
        depthOf.put(root, 0);
        queue.add(root);

        while (!queue.isEmpty()) {
            Function f = queue.poll();
            int d = depthOf.get(f);
            Set<Function> cs;
            try { cs = new TreeSet<>(Comparator.comparing(Function::getEntryPoint)); cs.addAll(f.getCalledFunctions(monitor)); }
            catch (Exception e) { cs = new TreeSet<>(); }
            callees.put(f, cs);
            if (d >= maxDepth) continue;
            for (Function c : cs) {
                if (!depthOf.containsKey(c)) { depthOf.put(c, d + 1); queue.add(c); }
            }
        }

        // edges + nodes
        new File(OUT).mkdirs();
        try (PrintWriter en = new PrintWriter(new File(OUT + "calltree_nodes.csv"), "UTF-8");
             PrintWriter ee = new PrintWriter(new File(OUT + "calltree_edges.csv"), "UTF-8")) {
            en.println("depth,address,name,bytes,callees,subsystems");
            ee.println("caller_address,caller_name,callee_address,callee_name");
            for (Map.Entry<Function, Integer> e : depthOf.entrySet()) {
                Function f = e.getKey();
                Set<Function> cs = callees.getOrDefault(f, Collections.emptySet());
                Set<String> subs = subsystemsOf(f, cs);
                en.println(e.getValue() + "," + f.getEntryPoint() + "," + f.getName() + "," +
                        f.getBody().getNumAddresses() + "," + cs.size() + "," + String.join(";", subs));
                for (Function c : cs) {
                    ee.println(f.getEntryPoint() + "," + f.getName() + "," +
                            c.getEntryPoint() + "," + c.getName());
                }
            }
        }

        // summary
        Map<Integer, Integer> byDepth = new TreeMap<>();
        Map<String, Integer> bySub = new TreeMap<>();
        int pure = 0, total = depthOf.size();
        long bytes = 0;
        for (Map.Entry<Function, Integer> e : depthOf.entrySet()) {
            byDepth.merge(e.getValue(), 1, Integer::sum);
            bytes += e.getKey().getBody().getNumAddresses();
            Set<String> subs = subsystemsOf(e.getKey(), callees.getOrDefault(e.getKey(), Collections.emptySet()));
            if (subs.isEmpty()) pure++;
            for (String s : subs) bySub.merge(s, 1, Integer::sum);
        }
        println("CALLTREE reachable=" + total + " bytes=" + bytes + " internal_only=" + pure);
        println("CALLTREE by depth: " + byDepth);
        println("CALLTREE functions calling into each subsystem: " + bySub);

        int all = fm.getFunctionCount();
        println("CALLTREE uncovered_by_walk=" + (all - total) + " of " + all + " total functions");
    }
}
