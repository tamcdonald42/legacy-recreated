import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataUtilities;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.symbol.Reference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;

/**
 * For the biggest internal ("pure engine") functions, pull the evidence you need to name them:
 * referenced string literals and named callees.
 *
 * Reads work/ghidra-out/calltree_nodes.csv, annotates the top N functions with no boundary
 * subsystem, writes work/ghidra-out/annotation.csv and prints a compact summary.
 *
 * Usage: -postScript Annotate.java [maxFunctions] [minBytes]
 */
public class Annotate extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";

    private String stringAt(Address a) {
        try {
            if (!currentProgram.getMemory().contains(a)) return null;
            ghidra.program.model.mem.MemoryBlock blk = currentProgram.getMemory().getBlock(a);
            if (blk == null || !blk.isInitialized()) return null;
            if (blk.getName().toLowerCase().contains("text")) return null;   // code
            byte[] buf = new byte[96];
            int n = currentProgram.getMemory().getBytes(a, buf);
            if (n < 4) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                byte c = buf[i];
                if (c == 0) break;
                if (c < 32 || c > 126) return null;      // not a plain C string
                sb.append((char) c);
            }
            if (sb.length() < 4) return null;
            String v = sb.toString().trim();
            if (v.length() > 70) v = v.substring(0, 70) + "...";
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean crtNoise(String n) {
        return n.startsWith("_") || n.startsWith("$") || n.contains("ftol") || n.contains("_CI");
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        int maxFn = (args.length > 0) ? Integer.parseInt(args[0]) : 15;
        long minBytes = (args.length > 1) ? Long.parseLong(args[1]) : 1500L;

        // pick candidates: internal-only, >= minBytes, deepest-first by size
        List<String[]> cand = new ArrayList<>();
        File csv = new File(OUT + "calltree_nodes.csv");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csv), "UTF-8"))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 6) continue;
                if (!p[5].isEmpty()) continue;              // has boundary callees -> skip
                if (Long.parseLong(p[3]) < minBytes) continue;
                cand.add(p);
            }
        } catch (Exception e) { println("ERROR reading " + csv + ": " + e.getMessage()); return; }
        cand.sort((a, b) -> Long.compare(Long.parseLong(b[3]), Long.parseLong(a[3])));
        if (cand.size() > maxFn) cand = cand.subList(0, maxFn);

        FunctionManager fm = currentProgram.getFunctionManager();
        Memory mem = currentProgram.getMemory();

        try (PrintWriter pw = new PrintWriter(new File(OUT + "annotation.csv"), "UTF-8")) {
            pw.println("address,name,bytes,subsystems,named_callees,strings");
            for (String[] row : cand) {
                Address a = currentProgram.getAddressFactory().getDefaultAddressSpace()
                        .getAddress(Long.parseLong(row[1], 16));
                Function f = fm.getFunctionAt(a);
                if (f == null) continue;

                Set<String> strs = new LinkedHashSet<>();
                Set<String> namedCals = new TreeSet<>();
                for (Address ia : f.getBody().getAddresses(true)) {
                    Instruction insn = getInstructionAt(ia);
                    if (insn == null) continue;
                    for (Reference r : insn.getReferencesFrom()) {
                        Address to = r.getToAddress();
                        if (to == null) continue;
                        if (!mem.contains(to)) continue;
                        if (insn.getFlowType().isCall()) {
                            Function t = fm.getFunctionAt(to);
                            if (t != null && !t.getName().startsWith("FUN_") && !crtNoise(t.getName())) namedCals.add(t.getName());
                        }
                        String s = stringAt(to);
                        if (s != null && s.length() > 2) strs.add(s);
                    }
                }
                pw.println(a + "," + f.getName() + "," + f.getBody().getNumAddresses() + "," + row[5] + "," +
                        String.join("|", namedCals) + "," + String.join("|", strs));
                println("ANNOTATE " + a + " " + f.getName() + " (" + f.getBody().getNumAddresses() + "B) " +
                        "calls=[" + String.join(",", namedCals) + "] " +
                        "strings=[" + String.join(" / ", strs.size() > 6 ? new ArrayList<>(strs).subList(0, 6) : strs) + "]");
            }
        }
    }
}
