import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.symbol.SymbolTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Who calls this API? Handles both direct calls and calls through the import pointer table
 * (the usual shape: instruction -> IAT slot -> external symbol).
 *
 * Usage: -postScript WhoCalls.java BitBlt,StretchBlt,CreateCompatibleBitmap,IDirectDraw_CreateSurface
 */
public class WhoCalls extends GhidraScript {

    private Map<String, List<String>> callers(Address target) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Reference r : currentProgram.getReferenceManager().getReferencesTo(target)) {
            Address from = r.getFromAddress();
            Instruction insn = getInstructionAt(from);
            if (insn != null) {
                Function f = getFunctionContaining(from);
                counts.merge(f == null ? "<no function>" : f.getName(), 1, Integer::sum);
                continue;
            }
            // from a data slot (IAT): find who references the slot
            for (Reference r2 : currentProgram.getReferenceManager().getReferencesTo(from)) {
                Instruction insn2 = getInstructionAt(r2.getFromAddress());
                if (insn2 == null) continue;
                Function f = getFunctionContaining(r2.getFromAddress());
                counts.merge(f == null ? "<no function>" : f.getName(), 1, Integer::sum);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            List<String> v = new ArrayList<>();
            v.add(String.valueOf(e.getValue()));
            out.put(e.getKey(), v);
        }
        return out;
    }

    @Override
    public void run() throws Exception {
        List<String> names = new ArrayList<>();
        for (String a : getScriptArgs()) for (String p : a.split(",")) if (!p.trim().isEmpty()) names.add(p.trim());
        SymbolTable st = currentProgram.getSymbolTable();

        for (String name : names) {
            Symbol sym = null;
            SymbolIterator it = st.getSymbols(name);
            while (it.hasNext()) {
                Symbol s = it.next();
                if (s.getAddress() != null) { sym = s; break; }
            }
            if (sym == null) { println("MISSING " + name); continue; }
            Map<String, List<String>> c = callers(sym.getAddress());
            if (c.isEmpty()) { println("NOCALLERS " + name + " @" + sym.getAddress()); continue; }
            StringBuilder sb = new StringBuilder("CALLERS " + name + " (" + c.size() + "):");
            for (Map.Entry<String, List<String>> e : c.entrySet())
                sb.append(" ").append(e.getKey()).append("x").append(e.getValue().get(0));
            println(sb.toString());
        }
    }
}
