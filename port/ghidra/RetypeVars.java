import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.symbol.SourceType;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Retype decompiler variables of one function, then re-decompile.
 *
 * Ghidra types the object pointer inside ActivateObject as `int` because the value arrives in
 * EAX from a __fastcall caller, so every field access renders as *(uint *)(iVar1 + 0x132)
 * instead of pObj->flags132. The global's type cannot fix that -- only the variable's type can.
 *
 * Usage: -postScript RetypeVars.java 0x428ec0 in_EAX=ObjSlot* iVar1=Obj*
 *        (no assignments -> just list the variables and their current types)
 */
public class RetypeVars extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";

    private DecompileResults decompile(Function f) {
        DecompInterface di = new DecompInterface();
        di.toggleCCode(true);
        di.toggleSyntaxTree(true);
        di.openProgram(currentProgram);
        return di.decompileFunction(f, 180, monitor);
    }

    @Override
    public void run() throws Exception {
        List<String> args = new ArrayList<>();
        for (String a : getScriptArgs()) for (String p : a.split(",")) if (!p.trim().isEmpty()) args.add(p.trim());
        if (args.isEmpty()) { println("ERROR: need an address"); return; }

        long addr = Long.parseLong(args.get(0).replace("0x", ""), 16);
        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
        Function f = currentProgram.getFunctionManager().getFunctionAt(a);
        if (f == null) { println("ERROR: no function at " + a); return; }

        DecompileResults res = decompile(f);
        if (!res.decompileCompleted()) { println("DECOMP FAILED: " + res.getErrorMessage()); return; }
        HighFunction hf = res.getHighFunction();

        List<HighSymbol> syms = new ArrayList<>();
        Iterator<HighSymbol> it = hf.getLocalSymbolMap().getSymbols();
        while (it.hasNext()) syms.add(it.next());

        if (args.size() == 1) {
            println("VARIABLES of " + f.getName() + ":");
            for (HighSymbol s : syms)
                println("  " + s.getName() + " : " + s.getDataType().getName());
            return;
        }

        DataTypeManager dtm = currentProgram.getDataTypeManager();
        for (int i = 1; i < args.size(); i++) {
            String[] kv = args.get(i).split(":");   // Ghidra splits script args on '='
            if (kv.length != 2) { println("  bad assignment: " + args.get(i)); continue; }
            String varName = kv[0].trim(), typeName = kv[1].trim();
            DataType dt;
            if (typeName.endsWith("*")) {
                DataType base = dtm.getDataType("/" + typeName.substring(0, typeName.length() - 1).trim());
                if (base == null) { println("  unknown base type in " + typeName); continue; }
                dt = new PointerDataType(base);
            } else {
                dt = dtm.getDataType("/" + typeName);
                if (dt == null) { println("  unknown type " + typeName); continue; }
            }
            boolean done = false;
            for (HighSymbol s : syms) {
                if (!s.getName().equals(varName)) continue;
                HighFunctionDBUtil.updateDBVariable(s, s.getName(), dt, SourceType.USER_DEFINED);
                println("  retyped " + varName + " -> " + dt.getName());
                done = true;
            }
            if (!done) println("  variable not found: " + varName);
        }

        DecompileResults res2 = decompile(f);
        if (!res2.decompileCompleted()) { println("RE-DECOMP FAILED: " + res2.getErrorMessage()); return; }
        String c = res2.getDecompiledFunction().getC();
        File out = new File(OUT + "decomp_" + Long.toHexString(addr) + "_retyped.c");
        try (PrintWriter pw = new PrintWriter(out, "UTF-8")) { pw.print(c); }
        println("RETYPED_DECOMP lines=" + c.split("\n").length + " file=" + out.getAbsolutePath());
    }
}
