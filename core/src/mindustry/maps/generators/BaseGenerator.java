package mindustry.maps.generators;

import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.BaseRegistry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BaseGenerator{
    private static final Vec2 axis = new Vec2(), rotator = new Vec2();

    private static final int range = 180;
    private static boolean insanity = false;

    private Tiles tiles;
    private Seq<Tile> cores;

    public static Block getDifficultyWall(int size, float difficulty){
        Seq<Block> wallsSmall = content.blocks().select(b -> b instanceof Wall && b.isVanilla() && b.size == size
            && !b.insulated && b.buildVisibility == BuildVisibility.shown
            && !(b instanceof Door)
            && b.isOnPlanet(state.getPlanet()));
        wallsSmall.sort(b -> b.buildTime);
        return wallsSmall.getFrac(difficulty * 0.91f);
    }

    public void generate(Tiles tiles, Seq<Tile> cores, Tile spawn, Team team, Sector sector, float difficulty){
        this.tiles = tiles;
        this.cores = cores;

        //don't generate bases when there are no loaded schematics
        if(bases.cores.isEmpty()) return;

        Mathf.rand.setSeed(sector.id);
        Mathf.rand.nextDouble();

        float bracketRange = 0.17f;
        float baseChance = Mathf.lerp(0.7f, 2.1f, difficulty);
        int wallAngle = 70; //180 for full coverage
        double resourceChance = 0.5 * baseChance;
        double nonResourceChance = 0.002 * baseChance;
        int passes = difficulty < 0.4 ? 1 : difficulty < 0.8 ? 3 : 5;

        Block wall = getDifficultyWall(1, difficulty), wallLarge = getDifficultyWall(2, difficulty);

        for(Tile tile : cores){
            tile.clearOverlay();
            Schematics.placeLoadout(bases.cores.getFrac((difficulty + Mathf.rand.range(0.4f)) / 1.4f).schematic, tile.x, tile.y, team, false);

            //fill core with every type of item (even non-material)
            Building entity = tile.build;
            for(Item item : content.items()){
                entity.items.add(item, entity.block.itemCapacity);
            }
        }

        for(int i = 0; i < passes; i++){
            //random schematics
            pass(tile -> {
                if(!tile.block().alwaysReplace) return;

                if(((tile.overlay().asFloor().itemDrop != null || (tile.drop() != null && Mathf.rand.chance(nonResourceChance)))
                || (tile.floor().liquidDrop != null && Mathf.rand.chance(nonResourceChance * 2))) && Mathf.rand.chance(resourceChance)){
                    Seq<BasePart> parts = bases.forResource(tile.drop() != null ? tile.drop() : tile.floor().liquidDrop);
                    if(!parts.isEmpty()){
                        tryPlace(parts.getFrac(difficulty + Mathf.rand.range(bracketRange)), tile.x, tile.y, team, Mathf.rand);
                    }
                }else if(Mathf.rand.chance(nonResourceChance)){
                    tryPlace(bases.parts.getFrac(Mathf.rand.random(1f)), tile.x, tile.y, team, Mathf.rand);
                }
            });
        }

        //replace walls with the correct type (disabled)
        if(false)
        pass(tile -> {
            if(tile.block() instanceof Wall && tile.team() == team && tile.block() != wall && tile.block() != wallLarge){
                tile.setBlock(tile.block().size == 2 ? wallLarge : wall, team);
            }
        });

        if(wallAngle > 0){

            //small walls
            pass(tile -> {

                if(tile.block().alwaysReplace){
                    boolean any = false;

                    for(Point2 p : Geometry.d4){
                        Tile o = tiles.get(tile.x + p.x, tile.y + p.y);

                        //do not block payloads
                        if(o != null && (o.block() instanceof PayloadConveyor || o.block() instanceof PayloadBlock)){
                            return;
                        }
                    }

                    for(Point2 p : Geometry.d8){
                        if(Angles.angleDist(Angles.angle(p.x, p.y), spawn.angleTo(tile)) > wallAngle){
                            continue;
                        }

                        Tile o = tiles.get(tile.x + p.x, tile.y + p.y);
                        if(o != null && o.team() == team && !(o.block() instanceof Wall) && !(o.block() instanceof ShockMine)){
                            any = true;
                            break;
                        }
                    }

                    if(any){
                        tile.setBlock(wall, team);
                    }
                }
            });

            //large walls
            pass(curr -> {
                int walls = 0;
                for(int cx = 0; cx < 2; cx++){
                    for(int cy = 0; cy < 2; cy++){
                        Tile tile = tiles.get(curr.x + cx, curr.y + cy);
                        if(tile == null || tile.block().size != 1 || (tile.block() != wall && !tile.block().alwaysReplace)) return;

                        if(tile.block() == wall){
                            walls ++;
                        }
                    }
                }

                if(walls >= 3){
                    curr.setBlock(wallLarge, team);
                }
            });
        }


        float coreDst = 10f * 8;

        //clear path for ground units
        for(Tile tile : cores){
            Astar.pathfind(tile, spawn, t -> t.team() == state.rules.waveTeam && !t.within(tile, coreDst) ? 100000 : t.floor().hasSurface() ? 1 : 10, t -> !t.block().isStatic()).each(t -> {
                if(!t.within(tile, coreDst)){
                    if(t.team() == state.rules.waveTeam){
                        t.setBlock(Blocks.air);
                    }

                    for(Point2 p : Geometry.d8){
                        Tile other = t.nearby(p);
                        if(other != null && other.team() == state.rules.waveTeam){
                            other.setBlock(Blocks.air);
                        }
                    }
                }
            });
        }
    }

    public void postGenerate(){
        if(tiles == null) return;

        for(Tile tile : tiles){
            if(tile.isCenter() && tile.team() == state.rules.waveTeam){
                if(tile.block() instanceof PowerNode){
                    tile.build.configureAny(new Point2[0]);
                    tile.build.placed();
                }else if(tile.block() instanceof Battery){
                    tile.build.power.status = 1f;
                }
            }
        }
    }

    void pass(Cons<Tile> cons){
        Tile core = cores.first();
        core.circle(range, (x, y) -> cons.get(tiles.getn(x, y)));
    }

    /**
     * Tries to place a base part at a certain location with a certain team.
     * @return success state
     * */
    public static boolean tryPlace(BasePart part, int x, int y, Team team, Rand rand){
        return tryPlace(part, x, y, team, rand, null);
    }

    /**
     * Tries to place a base part at a certain location with a certain team.
     * @return success state
     * */
    public static boolean tryPlace(BasePart part, int x, int y, Team team, Rand random, @Nullable Intc2 posc){
        int rotation = random.range(2);
        axis.set((int)(part.schematic.width / 2f), (int)(part.schematic.height / 2f));
        Schematic result = Schematics.rotate(part.schematic, rotation);

        rotator.set(part.centerX, part.centerY).rotateAround(axis, rotation * 90);
        //bottom left schematic corner
        int cx = x - (int)rotator.x;
        int cy = y - (int)rotator.y;

        if(!insanity){
            for(Stile tile : result.tiles){
                int realX = tile.x + cx, realY = tile.y + cy;
                if(isTaken(tile.block, realX, realY) || (tile.block == Blocks.oilExtractor && tile.block.sumAttribute(Attribute.oil, realX, realY) <= 0.001f)){
                    return false;
                }
            }
        }

        //only do callback after validation
        for(Stile tile : result.tiles){
            int realX = tile.x + cx, realY = tile.y + cy;
            if(posc != null){
                posc.get(realX, realY);
            }
        }

        if(part.required instanceof Item item){
            for(Stile tile : result.tiles){
                if(tile.block instanceof Drill && (!insanity || !isTaken(tile.block, tile.x + cx, tile.y + cy))){

                    tile.block.iterateTaken(tile.x + cx, tile.y + cy, (ex, ey) -> {
                        Tile placed = world.tiles.get(ex, ey);

                        if(placed == null) return;

                        if(placed.floor().hasSurface()){
                            set(placed, item);
                        }

                        Tile rand = world.tiles.getc(ex + random.range(1), ey + random.range(1));
                        if(rand.floor().hasSurface()){
                            //random ores nearby to make it look more natural
                            set(rand, item);
                        }
                    });
                }
            }
        }

        Schematics.place(result, cx + result.width/2, cy + result.height/2, team, false);

        //fill drills with items after placing
        if(part.required instanceof Item item){
            for(Stile tile : result.tiles){
                if(tile.block instanceof Drill){

                    var build = world.build(tile.x + cx, tile.y + cy);

                    if(build != null && build.block == tile.block){
                        build.items.add(item, build.block.itemCapacity);
                    }
                }
            }
        }

        return true;
    }

    static void set(Tile tile, Item item){
        if(bases.ores.containsKey(item)){
            tile.setOverlay(bases.ores.get(item));
        }else if(bases.oreFloors.containsKey(item)){
            tile.setFloor(bases.oreFloors.get(item));
        }
    }

    static boolean isTaken(Block block, int x, int y){
        if(state.teams.anyEnemyCoresWithin(state.rules.waveTeam, x * tilesize + block.offset, y * tilesize + block.offset, state.rules.enemyCoreBuildRadius + tilesize)) return true;

        int offsetx = -(block.size - 1) / 2;
        int offsety = -(block.size - 1) / 2;
        int pad = 1;

        for(int dx = -pad; dx < block.size + pad; dx++){
            for(int dy = -pad; dy < block.size + pad; dy++){
                if(overlaps(dx + offsetx + x, dy + offsety + y)){
                    return true;
                }
            }
        }

        return false;
    }

    static boolean overlaps(int x, int y){
        Tile tile = world.tiles.get(x, y);

        return tile == null || !tile.block().alwaysReplace || world.getDarkness(x, y) > 0;
    }
}
