import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.symbol.Reference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Who touches this global? For each address given, list the functions that reference it,
 * with read/write counts and one sample instruction per function. Writes = "MOV [addr], ...",
 * which is how you tell a flag that is set from one that is only tested.
 *
 * Usage: -postScript Xrefs.java 0x4ae960,0x4af8cc,0x4af8d0,0x4af8c4,0x4af8c8,0x4c90a0
 */
public class Xrefs extends GhidraScript {

    @Override
    public void run() throws Exception {
        java.util.List<String> specs = new java.util.ArrayList<>();
        for (String a0 : getScriptArgs()) for (String piece : a0.split(","))
            if (!piece.trim().isEmpty()) specs.add(piece.trim());
        if (specs.isEmpty()) specs.add("0x4ae960");
        for (String spec : specs) {
            Address a = currentProgram.getAddressFactory().getDefaultAddressSpace()
                    .getAddress(Long.parseLong(spec.replace("0x", ""), 16));

            Map<String, int[]> perFn = new TreeMap<>();   // name -> {reads, writes}
            Map<String, String> sample = new LinkedHashMap<>();
            int total = 0;

            for (Reference r : currentProgram.getReferenceManager().getReferencesTo(a)) {
                Instruction insn = getInstructionAt(r.getFromAddress());
                if (insn == null) continue;
                String mnem = insn.getMnemonicString().toUpperCase();
                String txt = insn.toString();
                // CMP/TEST only read; MOV/arithmetic with the address in operand 0 write.
                boolean isWrite = false;
                if (!mnem.equals("CMP") && !mnem.equals("TEST") && !mnem.equals("PUSH")) {
                    String[] ops = txt.split("\\s+", 2);
                    if (ops.length > 1) {
                        String dest = ops[1].split(",")[0].toLowerCase();
                        isWrite = dest.contains("0x" + Long.toHexString(a.getOffset()));
                    }
                }
                Function f = getFunctionContaining(r.getFromAddress());
                String fn = (f == null) ? "<no function>" : f.getName();
                int[] c = perFn.computeIfAbsent(fn, k -> new int[2]);
                if (isWrite) c[1]++; else c[0]++;
                sample.putIfAbsent(fn, mnem + " " + txt);
                total++;
            }

            println("=== XREFS " + a + " (" + spec + ") refs=" + total + " functions=" + perFn.size());
            for (Map.Entry<String, int[]> e : perFn.entrySet()) {
                println("   " + e.getKey() + "  read=" + e.getValue()[0] + " write=" + e.getValue()[1] +
                        "   e.g. " + sample.get(e.getKey()));
            }
        }
    }
}
