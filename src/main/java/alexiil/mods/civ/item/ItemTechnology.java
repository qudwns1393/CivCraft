package alexiil.mods.civ.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import alexiil.mods.civ.CivCraft;
import alexiil.mods.civ.api.tech.TechResearchedEvent.ItemTechResearchedEvent;
import alexiil.mods.civ.api.tech.TechTree;
import alexiil.mods.civ.api.tech.TechTree.Tech;
import alexiil.mods.civ.api.tech.unlock.IUnlockable;
import alexiil.mods.civ.item.ItemTechBag.TechProgress;
import alexiil.mods.civ.utils.TechUtils;
import alexiil.mods.lib.EChatColours;
import alexiil.mods.lib.item.IChangingItemString;
import alexiil.mods.lib.item.ItemBase;
import alexiil.mods.lib.nbt.NBTUtils;

/** This represents an item that can store a technology. The Technological progress (and which tech it is) is stored in
 * NBT, in this form:
 * <ul>
 * <li>"name" -> the name of the tech in question (e.g: "agriculture")</li>
 * <li>"packs" -> an integer array of the science packs added to this item</li>
 * </ul> */
public class ItemTechnology extends ItemBase {
    public enum EResearchState {
        PENDING(EChatColours.WHITE), RESEARCHING(EChatColours.AQUA), RESEARCHED(EChatColours.GOLD);

        public final EChatColours colour;

        EResearchState(EChatColours colour) {
            this.colour = colour;
        }
    }

    public ItemTechnology(String name) {
        super(name, CivCraft.instance);
        addInfo(new IChangingItemString() {
            @Override
            public String[] getString(ItemStack i, EntityPlayer player) {
                Tech t = getTech(i);
                if (t == null)
                    return new String[] { "Not a valid research item" };
                EResearchState s = getState(i);
                String info = EChatColours.DARK_PURPLE + "Craft with beakers to research";
                String colour = s.colour.toString();
                if (s == EResearchState.RESEARCHED)
                    info = null;
                return new String[] { info, colour + s.toString().toLowerCase() + " " + t.name };
            }
        });
        addInfo(new IChangingItemString() {
            @Override
            public String[] getString(ItemStack i, EntityPlayer player) {
                if (getState(i) == EResearchState.RESEARCHED)
                    return new String[0];
                Tech t = getTech(i);
                int[] got = getScienceCount(i);
                int[] required = t.getSciencePacksNeeded();
                if (got.length < required.length)
                    got = Arrays.copyOf(got, required.length);
                if (required.length < got.length)
                    required = Arrays.copyOf(required, got.length);
                String[] gotton = new String[required.length];
                int lastNull = 4646;// Random big number, okay this would cause issues if there are more than 4646
                                    // science packs, but the just seems unlikely
                for (int idx = 0; idx < gotton.length; idx++)
                    if (got[idx] != 0 || required[idx] != 0) {
                        int diff = required[idx] - got[idx];
                        gotton[idx] = (diff == 0 ? EChatColours.GRAY : EChatColours.BLUE) + "";
                        gotton[idx] += got[idx] + "/" + required[idx];
                    }
                    else
                        lastNull = idx;
                if (lastNull < gotton.length)
                    gotton = Arrays.copyOfRange(gotton, 0, lastNull);
                return gotton;
            }
        });
        addShiftInfo(new IChangingItemString() {
            @Override
            public String[] getString(ItemStack i, EntityPlayer player) {
                Tech t = getTech(i);
                if (t == null) {
                    NBTTagCompound nbt = i.getTagCompound();
                    if (nbt == null)
                        return new String[] { "Null NBT" };
                    String s = NBTUtils.toString(nbt).replace("\t", "  ");
                    return s.split("\n");
                }

                Tech[] parents = t.getParentTechs();
                Tech[] children = t.getChildTechs();
                IUnlockable[] usages = t.getShownUnlockables();
                List<String> strings = new ArrayList<String>();
                strings.add(parents.length == 0 ? "Doesn't require any techs" : "Requires these techs:");

                for (Tech parent : parents)
                    strings.add(TechUtils.getColour(parent) + " -" + parent.getLocalizedName());

                strings.add(children.length == 0 ? "Doesn't lead to any other techs" : "Leads to these techs:");

                for (Tech child : children)
                    strings.add(TechUtils.getColour(child) + " -" + child.getLocalizedName());

                strings.add(usages.length == 0 ? "Doesn't unlock anything" : "Unlocks these things:");

                for (IUnlockable unlock : usages)
                    strings.addAll(unlock.getDescription());

                return strings.toArray(new String[strings.size()]);
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        int[] singleInt = new int[] { 1 };
        if (TechTree.currentTree == null)
            return;
        for (String s : TechTree.currentTree.getTechs().keySet()) {
            Tech t = TechTree.currentTree.getTech(s);
            int[] packs = t.getSciencePacksNeeded();
            list.add(getItemForTech(t, new int[0]));
            list.add(getItemForTech(t, singleInt));
            if (!Arrays.equals(packs, singleInt))
                list.add(getItemForTech(t, packs));
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Tech t = getTech(stack);
        if (t == null)
            return "THE TECH WAS NULL! (this is a bad item!)";
        return super.getItemStackDisplayName(stack) + " " + t.getLocalizedName();
    }

    @Override
    public ItemStack getContainerItem(ItemStack i) {
        return i.copy();
    }

    @Override
    public boolean hasContainerItem(ItemStack i) {
        return true;
    }

    /** @return An integer array, containing the numbers of each type of pack that the given item has */
    public int[] getScienceCount(ItemStack item) {
        if (item == null || !item.hasTagCompound())
            return new int[0];
        NBTTagCompound nbt = item.getTagCompound();
        return nbt.getIntArray(Tech.SCIENCE_PACKS);
    }

    /** @param item
     *            The item to set the science to
     * @param science
     *            The science values to set */
    public void setScienceCount(ItemStack item, int[] science) {
        if (item == null)
            return;
        NBTTagCompound nbt = item.getTagCompound();
        nbt.setIntArray(Tech.SCIENCE_PACKS, science);
        item.setTagCompound(nbt);
        item.setItemDamage(getItemDamageForScience(getTech(item), science));
    }

    /** @param s
     *            The ItemStack to add science to
     * @param toAdd
     *            The packs to add */
    public void addPackCount(ItemStack s, int[] toAdd) {
        int[] already = getScienceCount(s);
        if (toAdd.length > already.length)
            already = Arrays.copyOf(already, toAdd.length);
        if (already.length > toAdd.length)
            toAdd = Arrays.copyOf(toAdd, already.length);
        for (int idx = 0; idx < already.length; idx++)
            already[idx] += toAdd[idx];
        setScienceCount(s, already);
    }

    public Tech getTech(ItemStack item) {
        if (item == null || !item.hasTagCompound())
            return null;
        NBTTagCompound nbt = item.getTagCompound();
        String name = nbt.getString(Tech.NAME);
        if (name == null)
            return null;
        if (TechTree.currentTree == null)
            return null;
        return TechTree.currentTree.getTech(name.intern());
    }

    public EResearchState getState(ItemStack i) {
        return EResearchState.values()[i.getItemDamage()];
    }

    public ItemStack getItemForTech(TechProgress progress) {
        if (progress == null)
            return null;
        return getItemForTech(progress.tech, progress.progress);
    }

    public ItemStack getItemForTech(Tech t, int[] packs) {
        if (t == null || packs == null)
            return null;
        int type = getItemDamageForScience(t, packs);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(Tech.NAME, t.name);
        nbt.setIntArray(Tech.SCIENCE_PACKS, packs);
        ItemStack item = new ItemStack(this, 1, type);
        item.setTagCompound(nbt);
        return item;
    }

    /** @param tech
     *            The tech to check against
     * @param packs
     *            The packs to check with
     * @return An integer;
     *         <p>
     *         0 if the array consisted of 0's, or was 0 length
     *         <p>
     *         OR 1 if any index of the array was less than the number required for that tech
     *         <p>
     *         OR 2 if all index's of the array were greater than or equal to what is required to unlock that tech */
    public int getItemDamageForScience(Tech tech, int[] packs) {
        int type = 0;
        boolean all = true;
        int[] required = tech.getSciencePacksNeeded();
        for (int idx = 0; idx < required.length; idx++)
            if (idx == packs.length) {
                all = false;
                break;
            }
            else if (packs[idx] < required[idx]) {
                if (packs[idx] > 0)
                    type = 1;
                all = false;
            }
            else if (packs[idx] >= required[idx] && all)
                type = 1;
        if (all)
            type = 2;
        return type;
    }

    /** @return An integer array, containing the numbers of packs required to complete this. This will never be null, but
     *         it may be an empty array (so, []) */
    public int[] getSciencePacksRequired(ItemStack i) {
        if (i == null)
            return new int[0];
        int[] current = getScienceCount(i);
        int[] techNeeded = getTech(i).getSciencePacksNeeded();
        int[] needed = new int[techNeeded.length];
        for (int idx = 0; idx < techNeeded.length; idx++)
            if (idx >= current.length)
                needed[idx] = techNeeded[idx];
            else
                needed[idx] = techNeeded[idx] - current[idx];
        return needed;
    }

    public ItemStack getStartingTech() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(Tech.NAME, "agriculture");
        nbt.setIntArray(Tech.SCIENCE_PACKS, new int[0]);
        ItemStack item = new ItemStack(this, 1, 0);
        item.setTagCompound(nbt);
        return item;
    }

    @Override
    public void initModel() {
        String mesherName = CivCraft.instance.meta.modId + ":" + name + "_";
        String[] names = new String[] { mesherName + "none", mesherName + "some", mesherName + "all" };
        ItemModelMesher mesher = Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
        for (int i = 0; i < 3; i++)
            mesher.register(this, i, new ModelResourceLocation(names[i], "inventory"));
        ModelBakery.addVariantName(this, names);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn) {
        Tech t = getTech(itemStackIn);
        EResearchState state = getState(itemStackIn);
        if (state != EResearchState.RESEARCHED)
            return itemStackIn;
        if (TechUtils.hasTech(t, playerIn))
            return itemStackIn;
        MinecraftForge.EVENT_BUS.post(new ItemTechResearchedEvent(itemStackIn, playerIn));
        return itemStackIn;

    }
}
