import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import java.io.File;
import java.io.PrintWriter;

/**
 * Dump every recovered function to CSV so the decompilation effort can be sized.
 * Run as a -postScript after analysis.
 */
public class Inventory extends GhidraScript {
    @Override
    public void run() throws Exception {
        File dir = new File("H:/ghidra/work/ghidra-out");
        dir.mkdirs();
        File out = new File(dir, "function_inventory.csv");

        FunctionManager fm = currentProgram.getFunctionManager();
        int n = 0;
        long totalBytes = 0;
        int thunks = 0, noret = 0, external = 0;

        try (PrintWriter pw = new PrintWriter(out, "UTF-8")) {
            pw.println("address,name,bytes,params,thunk,noReturn,external");
            for (Function f : fm.getFunctions(true)) {
                long sz = f.getBody().getNumAddresses();
                pw.println(f.getEntryPoint() + "," + f.getName() + "," + sz + "," +
                        f.getParameterCount() + "," + f.isThunk() + "," +
                        f.hasNoReturn() + "," + f.isExternal());
                n++;
                totalBytes += sz;
                if (f.isThunk()) thunks++;
                if (f.hasNoReturn()) noret++;
                if (f.isExternal()) external++;
            }
        }
        println("INVENTORY functions=" + n + " bytes=" + totalBytes +
                " thunks=" + thunks + " noreturn=" + noret + " external=" + external);
        println("INVENTORY file=" + out.getAbsolutePath());
    }
}
