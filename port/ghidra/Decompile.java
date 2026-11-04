import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decompile one function and report readability metrics.
 *
 * Usage: -postScript Decompile.java 0x430500 [timeoutSeconds]
 * Writes work/ghidra-out/decomp_<addr>.c and prints metrics.
 */
public class Decompile extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";

    private static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    private static int countRegex(String hay, String re) {
        Matcher m = Pattern.compile(re).matcher(hay);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        long addr = Long.parseLong((args.length > 0 ? args[0] : "0x430500").replace("0x", ""), 16);
        int timeout = (args.length > 1) ? Integer.parseInt(args[1]) : 180;

        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
        FunctionManager fm = currentProgram.getFunctionManager();
        Function f = fm.getFunctionAt(a);
        if (f == null) { println("ERROR: no function at " + a); return; }

        DecompInterface di = new DecompInterface();
        di.toggleCCode(true);
        di.toggleSyntaxTree(true);
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, timeout, monitor);
        if (!res.decompileCompleted()) {
            println("DECOMP FAILED: " + res.getErrorMessage());
            return;
        }
        String c = res.getDecompiledFunction().getC();

        new File(OUT).mkdirs();
        File out = new File(OUT + "decomp_" + Long.toHexString(addr) + ".c");
        try (PrintWriter pw = new PrintWriter(out, "UTF-8")) { pw.print(c); }

        // metrics
        Set<String> globals = new TreeSet<>();
        Matcher m = Pattern.compile("\\b(?:DAT|UNK|PTR|BYTE|WORD|DWORD|QWORD|LAB|FLOAT|DOUBLE)_([0-9a-fA-F]{5,8})\\b")
                .matcher(c);
        while (m.find()) globals.add(m.group(0));

        Set<String> callees = new TreeSet<>();
        Matcher m2 = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(c);
        while (m2.find()) {
            String n = m2.group(1);
            if (!n.equals("if") && !n.equals("while") && !n.equals("for") && !n.equals("switch")
                    && !n.equals("return") && !n.equals("sizeof") && !n.equals("do")) callees.add(n);
        }

        println("DECOMP " + a + " " + f.getName() + " file=" + out.getAbsolutePath());
        println("DECOMP lines=" + c.split("\n").length +
                " bytes=" + c.length());
        println("DECOMP loops: while=" + count(c, "while (") + " do=" + count(c, "do {") +
                " for=" + count(c, "for (") +
                " | switch=" + count(c, "switch (") + " case=" + count(c, "case "));
        println("DECOMP ifs=" + count(c, "if (") + " else=" + count(c, "else") +
                " gotos=" + count(c, "goto ") + " labels=" + countRegex(c, "(?m)^\\s*LAB_[0-9a-fA-F]+:"));
        println("DECOMP globals_touched=" + globals.size() + " distinct_callees=" + callees.size());
        println("DECOMP globals_sample=" + new java.util.ArrayList<>(globals).subList(0, Math.min(12, globals.size())));
        println("DECOMP callees_sample=" + new java.util.ArrayList<>(callees).subList(0, Math.min(12, callees.size())));
        println("DECOMP unaff_noise=" + count(c, "unaff_") + " instack_noise=" + count(c, "in_stack_"));
    }
}
