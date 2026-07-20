package com.pgmacdesign.mc3dprint.machine.sorter;

import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;

import java.util.Set;

/**
 * Topology-only discovery capability that lets a Filament Tier Item Sorter locate the
 * Filament Winders reachable from one of its faces. Mirrors {@link com.pgmacdesign.mc3dprint.fu.IFilamentSource}:
 *
 * <ul>
 *   <li>a <b>winder</b> adds itself</li>
 *   <li>a <b>cable</b> floods its network and adds every winder across it, exactly as
 *       {@code IFilamentSource.collectSources} does for racks</li>
 *   <li>the <b>sorter</b> queries its six faces and unions the results (identity-deduped);
 *       it never implements the capability</li>
 * </ul>
 *
 * <p>The cable NEVER carries the items — it only reports which winders exist. The sorter
 * then inserts directly into each winder's own {@code IItemHandler}. The reported set is
 * <em>positions resolved to live block entities</em>; the sorter re-validates each one
 * (still loaded, still a winder, still the tier it thinks) at insert time, so a stale
 * cable-cache entry can never route an item into a removed or re-tiered winder.
 */
public interface IWinderRouting {

    /** Adds the winders reachable through this source to {@code out} (identity-deduped by the caller). */
    void collectWinders(Set<WinderBlockEntity> out);
}
