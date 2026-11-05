import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeConflictHandler;
import ghidra.program.model.data.EnumDataType;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolTable;

/**
 * Step 1 of the readability pass: give the state machine real types.
 *
 *   DAT_004ae978 -> g_gameState  (enum GameState)
 *   DAT_004ae960 -> g_shutdown   (int)
 *
 * Enum member names are deliberately neutral (STATE_n): we know the numeric states from the
 * dispatch in WinMain_ and FUN_00430500, but not what they mean yet. Do not invent semantics.
 *
 * Applying the enum is what kills the "(int *)0x0" case labels and the "+ -5" arithmetic,
 * because Ghidra currently infers the global as a pointer.
 *
 * Usage: -postScript TypeState.java
 */
public class TypeState extends GhidraScript {

    private Address addr(long a) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(a);
    }

    private void rename(Address a, String name) {
        SymbolTable st = currentProgram.getSymbolTable();
        Symbol s = st.getPrimarySymbol(a);
        try {
            if (s == null) {
                st.createLabel(a, name, SourceType.USER_DEFINED);
                println("  created label " + name + " @ " + a);
            } else {
                String old = s.getName();
                s.setName(name, SourceType.USER_DEFINED);
                println("  renamed " + old + " -> " + name + " @ " + a);
            }
        } catch (Exception e) {
            println("  rename FAILED @ " + a + ": " + e.getMessage());
        }
    }

    private void applyType(Address a, DataType dt, int len) {
        try {
            Listing l = currentProgram.getListing();
            if (getInstructionAt(a) != null) { println("  SKIP (code) @ " + a); return; }
            Data d = getDataAt(a);
            if (d != null && d.getDataType().isEquivalent(dt)) { println("  already typed @ " + a); return; }
            if (d != null) clearListing(a, a.add(len - 1));
            createData(a, dt);
            Data n = getDataAt(a);
            println("  typed " + a + " as " + (n == null ? "?" : n.getDataType().getName()));
        } catch (Exception e) {
            println("  applyType FAILED @ " + a + ": " + e.getMessage());
        }
    }

    @Override
    public void run() throws Exception {
        Address stateAddr = addr(0x4ae978);
        Address shutAddr = addr(0x4ae960);

        // --- enum GameState ---------------------------------------------------
        EnumDataType gs = new EnumDataType("GameState", 4);
        // states observed: dispatch in WinMain_ (2, 3-5, 0xb, 0xd, 0xe) and inner cases 0-4
        int[] seen = {0, 1, 2, 3, 4, 5, 11, 13, 14};
        for (int v : seen) gs.add("STATE_" + v, v);
        DataType registered = currentProgram.getDataTypeManager()
                .addDataType(gs, DataTypeConflictHandler.REPLACE_HANDLER);
        println("enum GameState registered with " + seen.length + " members");

        // --- object tables (established from FUN_00430500's update phases) -----
        // Each count is walked together with one base array; the base of each slot
        // holds a pointer to the object itself. Pairing is documented in the finding.
        Object[][] tabs = new Object[][] {
            {0x4af8cc, "g_objCount0"}, {0x4bf040, "g_objTable0"},
            {0x4af8d0, "g_objCount1"}, {0x4c90a0, "g_objTable1"},
            {0x4af8c4, "g_objCount2"}, {0x4c9100, "g_objTable2"},
            {0x4af8c8, "g_objCount3"}, {0x4c13f8, "g_objTable3"},
        };
        for (Object[] t : tabs) {
            rename(addr((Integer) t[0]), (String) t[1]);
        }

        // --- enum SubState (the second state variable, DAT_004af898) -----------
        // FUN_00430500 switches on (g_subState - 8) and (g_subState - 1): a phase
        // counter within the in-game state. Values observed 1..10; range 0..15 typed.
        EnumDataType ss = new EnumDataType("SubState", 4);
        for (int v = 0; v <= 15; v++) ss.add("SUB_" + v, v);
        DataType ssReg = currentProgram.getDataTypeManager()
                .addDataType(ss, DataTypeConflictHandler.REPLACE_HANDLER);
        println("enum SubState registered with 16 members");

        // --- renames ----------------------------------------------------------
        println("renaming globals:");
        rename(stateAddr, "g_gameState");
        rename(shutAddr, "g_flag_ae960");   // never written by analysed code; name kept neutral
        Address subAddr = addr(0x4af898);
        rename(subAddr, "g_subState");

        // --- types ------------------------------------------------------------
        println("applying types:");
        applyType(stateAddr, registered, 4);
        DataType int4 = ghidra.program.model.data.IntegerDataType.dataType;
        applyType(shutAddr, int4, 4);
        applyType(addr(0x4af898), ssReg, 4);

        println("TYPESTATE done");
    }
}
