import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.symbol.SourceType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;

/**
 * Seed a fresh analysis with the ground truth SRW already figured out.
 * Reads H:/ghidra/reference/SR/SRW-games/Septerra Core/SRW/x86/*.sci and applies:
 *
 *   global_aliases.sci        loc_ADDR,Name   -> rename label/function
 *   external_procedures.sci   loc_ADDR,Name   -> name cross-boundary function
 *   noret_procedures.sci      loc_ADDR        -> mark no-return
 *   fixup_interpret_as_code.sci loc_ADDR      -> disassemble + create function
 *   ignored_areas.sci         loc_ADDR,len    -> clear (data, not code)
 *
 * Then writes function_inventory_labeled.csv.
 */
public class GroundTruth extends GhidraScript {

    private static final String SCI = "H:/ghidra/reference/SR/SRW-games/Septerra Core/SRW/x86/";

    private Address addr(String s) {
        String h = s.trim();
        if (h.startsWith("loc_")) h = h.substring(4);
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(Long.parseLong(h, 16));
    }

    private void rename(Address a, String name, int[] stats) {
        try {
            FunctionManager fm = currentProgram.getFunctionManager();
            Function f = fm.getFunctionAt(a);
            if (f == null) {
                f = createFunction(a, name);
                if (f != null) { stats[1]++; return; }
                createLabel(a, name, true);
                stats[2]++;
                return;
            }
            f.setName(name, SourceType.IMPORTED);
            stats[0]++;
        } catch (Exception e) {
            stats[3]++;
            println("  rename failed at " + a + " -> " + name + ": " + e.getMessage());
        }
    }

    @Override
    public void run() throws Exception {
        int[] stats = new int[4]; // renamed, created, labelled, failed

        // --- global aliases + external procedures: names ---
        for (String file : new String[]{"global_aliases.sci", "external_procedures.sci"}) {
            File f = new File(SCI + file);
            if (!f.exists()) { println("missing " + file); continue; }
            int n = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split(",");
                    if (p.length < 2) continue;
                    rename(addr(p[0]), p[1].trim(), stats);
                    n++;
                }
            }
            println(file + ": " + n + " entries");
        }

        // --- no-return ---
        File noret = new File(SCI + "noret_procedures.sci");
        if (noret.exists()) {
            int n = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(noret))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Function f = currentProgram.getFunctionManager().getFunctionAt(addr(line));
                    if (f != null) { f.setNoReturn(true); n++; }
                }
            }
            println("noret_procedures.sci: " + n + " marked");
        }

        // --- code ranges the analyzer may have missed ---
        File code = new File(SCI + "fixup_interpret_as_code.sci");
        if (code.exists()) {
            int n = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(code))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Address a = addr(line);
                    if (getInstructionAt(a) == null) { disassemble(a); n++; }
                    if (currentProgram.getFunctionManager().getFunctionAt(a) == null) createFunction(a, null);
                }
            }
            println("fixup_interpret_as_code.sci: " + n + " disassembled");
        }

        // --- data ranges ---
        File ignored = new File(SCI + "ignored_areas.sci");
        if (ignored.exists()) {
            int n = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(ignored))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split(",");
                    if (p.length < 2) continue;
                    Address start = addr(p[0]);
                    long len = Long.parseLong(p[1].trim());
                    clearListing(start, start.add(len - 1));
                    n++;
                }
            }
            println("ignored_areas.sci: " + n + " ranges cleared");
        }

        println("GROUNDTRUTH renamed=" + stats[0] + " created=" + stats[1] +
                " labelled=" + stats[2] + " failed=" + stats[3]);

        // --- labeled inventory ---
        File dir = new File("H:/ghidra/work/ghidra-out");
        dir.mkdirs();
        File out = new File(dir, "function_inventory_labeled.csv");
        FunctionManager fm = currentProgram.getFunctionManager();
        int count = 0;
        long bytes = 0;
        try (PrintWriter pw = new PrintWriter(out, "UTF-8")) {
            pw.println("address,name,bytes,params,thunk,noReturn");
            for (Function f : fm.getFunctions(true)) {
                pw.println(f.getEntryPoint() + "," + f.getName() + "," +
                        f.getBody().getNumAddresses() + "," + f.getParameterCount() + "," +
                        f.isThunk() + "," + f.hasNoReturn());
                count++;
                bytes += f.getBody().getNumAddresses();
            }
        }
        println("LABELED_INVENTORY functions=" + count + " bytes=" + bytes + " file=" + out.getAbsolutePath());
    }
}
