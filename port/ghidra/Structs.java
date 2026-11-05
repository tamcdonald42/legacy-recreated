import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.ArrayDataType;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeComponent;
import ghidra.program.model.data.DataTypeConflictHandler;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.data.StructureDataType;
import ghidra.program.model.data.Undefined1DataType;
import ghidra.program.model.data.UnsignedShortDataType;
import ghidra.program.model.data.VoidDataType;

import java.io.File;
import java.io.PrintWriter;

/**
 * Define the engine's object slot as real struct types and apply them to .bss.
 *
 * Layout proven from the disassembly of BuildObjTables (0x428390):
 *   mov $0x4bf048,%esi / lea -0x8(%esi),%ebx   -> slot base 0x4bf040
 *   push $0xbe8 / call memset                   -> slot size 0xBE8
 *   mov %eax,(%ebx)                             -> slot[0] = object pointer
 *   movl $0xffffffff,0xb08(%esi)                -> slot+0xB10 = -1
 *   mov %eax,0xbc4(%esi) / 0xbc8(%esi)          -> slot+0xBCC, slot+0xBD0 = 1
 *   add $0xbe8,%esi                             -> stride 0xBE8
 *
 * The five 0x1C8 sub-structs come from StartSlotEffects (0x4299c0), which calls
 * StartAnimation at slot+0x228/0x3f0/0x5b8/0x780/0x948 -- spacing exactly 0x1C8 --
 * and switches on the selector at slot+0xB10.
 *
 * Usage: -postScript Structs.java
 */
public class Structs extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";
    private DataTypeManager dtm;

    private void put(StructureDataType st, int off, DataType dt, int len, String name, String cmt) {
        try {
            st.replaceAtOffset(off, dt, len, name, cmt);
        } catch (Exception e) {
            println("  WARN replaceAtOffset(" + Integer.toHexString(off) + ") " + e.getMessage());
        }
    }

    private StructureDataType buildEffect() {
        StructureDataType e = new StructureDataType("Effect", 0x1C8, dtm);
        put(e, 0x040, new PointerDataType(VoidDataType.dataType), 4, "animRoot", "resource/animation root");
        put(e, 0x044, UnsignedShortDataType.dataType, 2, "f044", null);
        put(e, 0x05c, IntegerDataType.dataType, 4, "state", "StartSlotEffects writes 2");
        put(e, 0x060, IntegerDataType.dataType, 4, "f060", "StartSlotEffects writes 2");
        put(e, 0x06c, IntegerDataType.dataType, 4, "f06c", "StartSlotEffects writes 1");
        put(e, 0x190, UnsignedShortDataType.dataType, 2, "frameIndex", "index into 0x230-byte frame table");
        put(e, 0x194, new PointerDataType(VoidDataType.dataType), 4, "animList", "StartAnimation appends here");
        put(e, 0x19c, Undefined1DataType.dataType, 1, "loopFlag", "StartAnimation picks flag 1 when 0");
        put(e, 0x1a4, new PointerDataType(VoidDataType.dataType), 4, "f1a4", "bytes +0xe/+0xf copied into obj");
        return e;
    }

    private StructureDataType buildSlot(DataType effect) {
        StructureDataType s = new StructureDataType("ObjSlot", 0xBE8, dtm);
        put(s, 0x000, new PointerDataType(VoidDataType.dataType), 4, "obj", "object pointer");
        put(s, 0x004, IntegerDataType.dataType, 4, "animRoot", "copy of obj->0x164");
        put(s, 0x008, IntegerDataType.dataType, 4, "f008", "*(obj->0x164 + 8)");
        put(s, 0x228, new ArrayDataType(effect, 5, effect.getLength()), 5 * 0x1C8, "effects",
                "five 0x1C8 effect structs; 0x228 + 5*0x1C8 = 0xB10");
        put(s, 0xB10, IntegerDataType.dataType, 4, "activeEffect", "-1 = none; indexes effects[]");
        put(s, 0xBCC, IntegerDataType.dataType, 4, "fBCC", "initialised to 1");
        put(s, 0xBD0, IntegerDataType.dataType, 4, "fBD0", "initialised to 1");
        return s;
    }

    private String ctype(DataType dt) {
        String n = dt.getName();
        if (n.equals("undefined") || n.startsWith("undefined")) return null;
        if (dt instanceof PointerDataType) return "void *";
        if (n.equals("int")) return "int";
        if (n.equals("ushort")) return "unsigned short";
        if (n.equals("byte")) return "unsigned char";
        return n;
    }

    private String header(StructureDataType st) {
        StringBuilder sb = new StringBuilder();
        sb.append("typedef struct ").append(st.getName()).append(" {\n");
        int expect = 0;
        for (DataTypeComponent c : st.getComponents()) {
            DataType dt = c.getDataType();
            String ty = ctype(dt);
            if (ty == null) continue;                       // gap: emitted as one pad run
            int off = c.getOffset();
            if (off > expect) {
                sb.append(String.format("    uint8_t pad_%03X[0x%X];%n", expect, off - expect).replace("%n", "\n"));
            }
            String name = c.getFieldName() == null ? "field" : c.getFieldName();
            if (dt instanceof ArrayDataType) {
                ArrayDataType ad = (ArrayDataType) dt;
                String et = ctype(ad.getDataType());
                sb.append(String.format("    %s %s[%d];", et, name, ad.getNumElements()));
            } else {
                sb.append(String.format("    %s %s;", ty, name));
            }
            if (c.getComment() != null) sb.append("   /* ").append(c.getComment()).append(" */");
            sb.append("\n");
            expect = off + c.getLength();
        }
        if (expect < st.getLength()) {
            sb.append(String.format("    uint8_t pad_%03X[0x%X];\n", expect, st.getLength() - expect));
        }
        sb.append("} ").append(st.getName()).append(";\t/* size 0x")
          .append(Integer.toHexString(st.getLength())).append(" */\n");
        return sb.toString();
    }

    @Override
    public void run() throws Exception {
        dtm = currentProgram.getDataTypeManager();

        StructureDataType effect = buildEffect();
        StructureDataType slot = buildSlot(effect);

        DataType eReg = dtm.addDataType(effect, DataTypeConflictHandler.REPLACE_HANDLER);
        DataType sReg = dtm.addDataType(slot, DataTypeConflictHandler.REPLACE_HANDLER);
        println("STRUCT Effect size=0x" + Integer.toHexString(eReg.getLength()));
        println("STRUCT ObjSlot size=0x" + Integer.toHexString(sReg.getLength()));

        // Apply to the slot array in .bss: 0x4bf040 .. 0x4c90a0 leaves room for 13 slots.
        Address base = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(0x4bf040L);
        int count = 13;
        clearListing(base, base.add((long) count * 0xBE8 - 1));
        DataType arr = new ArrayDataType(sReg, count, sReg.getLength());
        try {
            createData(base, arr);
            println("APPLIED ObjSlot[" + count + "] at " + base);
        } catch (Exception e) {
            println("APPLY FAILED: " + e.getMessage());
        }
        ghidra.program.model.symbol.Symbol sym = getSymbolAt(base);
        if (sym != null) {
            sym.setName("g_objSlots", ghidra.program.model.symbol.SourceType.USER_DEFINED);
            println("renamed " + base + " -> g_objSlots");
        }

        new File(OUT).mkdirs();
        File h = new File(OUT + "objslot.h");
        try (PrintWriter pw = new PrintWriter(h, "UTF-8")) {
            pw.println("/* Septerra Core 1.04 object slot -- derived from BuildObjTables (0x428390)");
            pw.println("   and StartSlotEffects (0x4299c0). Offsets verified against the disassembly. */");
            pw.println();
            pw.print(header(effect));
            pw.println();
            pw.print(header(slot));
        }
        println("HEADER written " + h.getAbsolutePath());
    }
}
