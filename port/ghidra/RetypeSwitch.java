import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.symbol.SourceType;

import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;

/**
 * Retype a decompiler local variable (the switch operand) and re-decompile.
 *
 * Ghidra types FUN_00430500's switch operand as `int *` because it is derived from a global
 * by pointer arithmetic, which makes every case render as `case (int *)0x3:`. The global's
 * type cannot fix that -- only the variable's type can.
 *
 * Usage:
 *   -postScript RetypeSwitch.java 0x430500                  (list locals + types)
 *   -postScript RetypeSwitch.java 0x430500 piVar9 GameState (retype, then re-decompile)
 */
public class RetypeSwitch extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";

    private DecompileResults decompile(Function f) {
        DecompInterface di = new DecompInterface();
        di.toggleCCode(true);
        di.toggleSyntaxTree(true);
        di.openProgram(currentProgram);
        return di.decompileFunction(f, 300, monitor);
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        long addr = Long.parseLong((args.length > 0 ? args[0] : "0x430500").replace("0x", ""), 16);
        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
        Function f = currentProgram.getFunctionManager().getFunctionAt(a);
        if (f == null) { println("ERROR: no function at " + a); return; }

        DecompileResults res = decompile(f);
        if (!res.decompileCompleted()) { println("DECOMP FAILED: " + res.getErrorMessage()); return; }
        HighFunction hf = res.getHighFunction();

        // --- list locals ------------------------------------------------------
        println("LOCALS of " + f.getName() + " (" + a + "):");
        Iterator<HighSymbol> it = hf.getLocalSymbolMap().getSymbols();
        java.util.List<HighSymbol> all = new java.util.ArrayList<>();
        while (it.hasNext()) {
            HighSymbol s = it.next();
            all.add(s);
            DataType dt = s.getDataType();
            println("  " + s.getName() + " : " + (dt == null ? "?" : dt.getName()) +
                    " (size " + (dt == null ? -1 : dt.getLength()) + ", storage " + s.getStorage() + ")");
        }
        println("LOCALS count=" + all.size());

        if (args.length < 3) {
            println("LIST_ONLY (pass <var> <typeName> to retype)");
            return;
        }

        // --- retype -----------------------------------------------------------
        String varName = args[1];
        String typeName = args[2];
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        DataType newType;
        if (typeName.equalsIgnoreCase("int")) {
            newType = IntegerDataType.dataType;
        } else if (typeName.equalsIgnoreCase("int*") || typeName.equalsIgnoreCase("int *")) {
            newType = new ghidra.program.model.data.PointerDataType(IntegerDataType.dataType);
        } else {
            newType = dtm.getDataType("/" + typeName);
            if (newType == null) newType = dtm.getDataType(typeName);
            if (newType == null) { println("ERROR: unknown type " + typeName); return; }
        }
        println("retyping " + varName + " -> " + newType.getName());

        boolean done = false;
        for (HighSymbol s : all) {
            if (!s.getName().equals(varName)) continue;
            try {
                HighFunctionDBUtil.updateDBVariable(s, s.getName(), newType, SourceType.USER_DEFINED);
                println("  retyped " + varName + " (was " + s.getDataType().getName() + ")");
                done = true;
            } catch (Exception e) {
                println("  retype FAILED: " + e.getMessage());
            }
        }
        if (!done) { println("ERROR: variable " + varName + " not found"); return; }

        // --- re-decompile with the new type -----------------------------------
        DisassembleResult2(f);
    }

    private void DisassembleResult2(Function f) throws Exception {
        DecompileResults res2 = decompile(f);
        if (!res2.decompileCompleted()) { println("RE-DECOMP FAILED: " + res2.getErrorMessage()); return; }
        String c = res2.getDecompiledFunction().getC();
        File out = new File(OUT + "decomp_" + f.getEntryPoint() + "_retyped.c");
        try (PrintWriter pw = new PrintWriter(out, "UTF-8")) { pw.print(c); }

        int cases = 0, i = 0;
        while ((i = c.indexOf("case ", i)) >= 0) { cases++; i += 5; }
        int ptrCases = 0; i = 0;
        while ((i = c.indexOf("case (int *)", i)) >= 0) { ptrCases++; i += 12; }

        println("RETYPED_DECOMP lines=" + c.split("\n").length + " bytes=" + c.length() +
                " cases=" + cases + " pointer_cases=" + ptrCases);
        println("RETYPED_DECOMP file=" + out.getAbsolutePath());
        println("RETYPED_DECOMP in_db=" + currentProgram.getFunctionManager().getFunctionAt(f.getEntryPoint()).getName());
    }
}
