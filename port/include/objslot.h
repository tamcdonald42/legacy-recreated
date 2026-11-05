/* Septerra Core 1.04 object slot -- derived from BuildObjTables (0x428390)
   and StartSlotEffects (0x4299c0). Offsets verified against the disassembly. */

typedef struct Effect {
    uint8_t pad_000[0x40];
    void * animRoot;   /* resource/animation root */
    unsigned short f044;
    uint8_t pad_046[0x16];
    int state;   /* StartSlotEffects writes 2 */
    int f060;   /* StartSlotEffects writes 2 */
    uint8_t pad_064[0x8];
    int f06c;   /* StartSlotEffects writes 1 */
    uint8_t pad_070[0x120];
    unsigned short frameIndex;   /* index into 0x230-byte frame table */
    uint8_t pad_192[0x2];
    void * animList;   /* StartAnimation appends here */
    uint8_t pad_198[0xC];
    void * f1a4;   /* bytes +0xe/+0xf copied into obj */
    uint8_t pad_1A8[0x20];
} Effect;	/* size 0x1c8 */

typedef struct ObjSlot {
    void * obj;   /* object pointer */
    int animRoot;   /* copy of obj->0x164 */
    int f008;   /* *(obj->0x164 + 8) */
    uint8_t pad_00C[0x21C];
    Effect effects[5];   /* five 0x1C8 effect structs; 0x228 + 5*0x1C8 = 0xB10 */
    int activeEffect;   /* -1 = none; indexes effects[] */
    uint8_t pad_B14[0xB8];
    int fBCC;   /* initialised to 1 */
    int fBD0;   /* initialised to 1 */
    uint8_t pad_BD4[0x14];
} ObjSlot;	/* size 0xbe8 */
