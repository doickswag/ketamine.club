package we.ketamine.club.features.modules.combat;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PistonChain {
   PistonChain() {
   }

   static PistonChain.Plan find(BlockPos target, Direction firstOutside, Direction secondOutside, PistonChain.World world) {
      if (firstOutside.getAxis().isHorizontal() && secondOutside.getAxis().isHorizontal() && firstOutside.getAxis() != secondOutside.getAxis()) {
         BlockPos crystal = target.relative(firstOutside).relative(secondOutside);
         BlockPos base = crystal.below();
         BlockPos first = crystal.relative(firstOutside);
         BlockPos second = crystal.relative(secondOutside);
         Direction firstPush = firstOutside.getOpposite();
         Direction secondPush = secondOutside.getOpposite();
         if (world.at(crystal).kind() != PistonChain.Kind.AIR
            || !world.canBreakPiston(first)
            || !emptyPlacement(world, first)
            || !emptyPlacement(world, second)) {
            return null;
         } else if (world.at(base).kind() != PistonChain.Kind.BASE && !emptyPlacement(world, base)) {
            return null;
         } else if (!world.powered(first, firstPush) && !world.powered(second, secondPush)) {
            Vec3 start = Vec3.atBottomCenterOf(crystal);
            Set<BlockPos> reserved = Set.of(first, second, crystal, base, crystal.above(), target);
            PistonChain.Plan best = null;
            double bestCost = Double.POSITIVE_INFINITY;

            for (Direction side : Direction.values()) {
               if (side != firstPush) {
                  BlockPos firstPower = first.relative(side);
                  if (!reserved.contains(firstPower)
                     && emptyPlacement(world, firstPower)
                     && !crystalBox(start).intersects(new AABB(firstPower))
                     && !poweredBy(second, secondPush, firstPower)) {
                     Vec3 intermediate = push(start, first, firstPush, world, Set.of(first, second, base, firstPower));
                     if (!(intermediate.distanceToSqr(start) < 0.25)) {
                        for (Direction secondSide : Direction.values()) {
                           if (secondSide != secondPush) {
                              BlockPos secondPower = second.relative(secondSide);
                              if (!reserved.contains(secondPower)
                                 && !secondPower.equals(firstPower)
                                 && emptyPlacement(world, secondPower)
                                 && !crystalBox(intermediate).intersects(new AABB(secondPower))
                                 && !poweredBy(first, firstPush, secondPower)) {
                                 Vec3 end = push(intermediate, second, secondPush, world, Set.of(second, base, firstPower, secondPower));
                                 if (!(end.distanceToSqr(intermediate) < 0.25) && insideTargetCorner(end, target)) {
                                    double cost = world.placementCost(first)
                                       + world.placementCost(second)
                                       + world.placementCost(firstPower)
                                       + world.placementCost(secondPower);
                                    if (!(cost >= bestCost)) {
                                       best = new PistonChain.Plan(
                                          first, firstPush, firstPower, second, secondPush, secondPower, crystal, base, start, intermediate, end
                                       );
                                       bestCost = cost;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            return best;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean insideTargetCorner(Vec3 position, BlockPos target) {
      double epsilon = 0.03125;
      return position.x >= target.getX() - epsilon
         && position.x <= target.getX() + 1 + epsilon
         && position.z >= target.getZ() - epsilon
         && position.z <= target.getZ() + 1 + epsilon
         && Math.abs(position.y - target.getY()) <= epsilon;
   }

   private static boolean emptyPlacement(PistonChain.World world, BlockPos pos) {
      return world.at(pos).kind() == PistonChain.Kind.AIR && world.canPlace(pos);
   }

   static AABB crystalBox(Vec3 position) {
      return new AABB(position.x - 1.0, position.y, position.z - 1.0, position.x + 1.0, position.y + 2.0, position.z + 1.0);
   }

   static Vec3 push(Vec3 start, BlockPos piston, Direction direction, PistonChain.World world, Set<BlockPos> blocks) {
      Vec3 position = start;
      Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
      AABB head = new AABB(piston.relative(direction));

      for (int tick = 0; tick < 2; tick++) {
         AABB before = head.move(normal.scale(tick * 0.5 - 1.0));
         AABB after = before.move(normal.scale(0.5));
         AABB box = crystalBox(position);
         if (before.minmax(after).intersects(box)) {
            double overlap = switch (direction) {
               case EAST -> after.maxX - box.minX;
               case WEST -> box.maxX - after.minX;
               case SOUTH -> after.maxZ - box.minZ;
               case NORTH -> box.maxZ - after.minZ;
               default -> 0.0;
            };
            if (!(overlap <= 0.0)) {
               Vec3 movement = normal.scale(Math.min(0.5, overlap) + 0.01);
               position = position.add(world.collide(box, movement, blocks));
            }
         }
      }

      return position;
   }

   static boolean poweredBy(BlockPos piston, Direction facing, BlockPos redstone) {
      for (Direction side : Direction.values()) {
         if (side != facing && piston.relative(side).equals(redstone)) {
            return true;
         }

         if (side != Direction.DOWN && piston.above().relative(side).equals(redstone)) {
            return true;
         }
      }

      return false;
   }

   private static boolean near(Vec3 actual, Vec3 expected) {
      double tolerance = 0.015625;
      return actual != null && actual.distanceToSqr(expected) <= tolerance * tolerance;
   }

   static enum Action {
      FIRST_PISTON,
      SECOND_PISTON,
      BASE,
      CRYSTAL,
      FIRST_POWER,
      REMOVE_FIRST_PISTON,
      SECOND_POWER,
      WAIT,
      DONE,
      FAILED;

      private Action() {
      }
   }

   record Cell(PistonChain.Kind kind, Direction facing, boolean extended) {
      boolean piston(Direction direction, boolean extended) {
         return this.kind == PistonChain.Kind.PISTON && this.facing == direction && this.extended == extended;
      }
   }

   static final class Execution {
      private final PistonChain.Plan plan;
      private final long timeoutMs;
      private PistonChain.Execution.Stage stage = PistonChain.Execution.Stage.FIRST;
      private long deadline;
      private boolean sent;

      Execution(PistonChain.Plan plan, long now, long timeoutMs) {
         if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("Positive confirmation timeout required");
         } else {
            this.plan = plan;
            this.timeoutMs = timeoutMs;
            this.deadline = now + timeoutMs;
         }
      }

      PistonChain.Action next(PistonChain.World world, long now) {
         if (this.stage == PistonChain.Execution.Stage.DONE) {
            return PistonChain.Action.DONE;
         } else if (this.stage == PistonChain.Execution.Stage.FAILED) {
            return PistonChain.Action.FAILED;
         } else if (now >= this.deadline) {
            return this.fail();
         } else {
            while (true) {
               switch (this.stage) {
                  case FIRST:
                     if (!world.at(this.plan.firstPiston()).piston(this.plan.firstPush(), false)) {
                        return this.pistonPlacement(world, this.plan.firstPiston(), this.plan.firstPush(), PistonChain.Action.FIRST_PISTON);
                     }

                     this.advance(PistonChain.Execution.Stage.SECOND, now);
                     break;
                  case SECOND:
                     if (!this.unpowered(world, this.plan.firstPiston(), this.plan.firstPush())) {
                        return this.fail();
                     }

                     if (!world.at(this.plan.secondPiston()).piston(this.plan.secondPush(), false)) {
                        return this.pistonPlacement(world, this.plan.secondPiston(), this.plan.secondPush(), PistonChain.Action.SECOND_PISTON);
                     }

                     this.advance(PistonChain.Execution.Stage.BASE, now);
                     break;
                  case BASE:
                     if (!this.bothReady(world)) {
                        return this.fail();
                     }

                     if (world.at(this.plan.base()).kind() != PistonChain.Kind.BASE) {
                        return this.blockPlacement(world, this.plan.base(), PistonChain.Action.BASE);
                     }

                     this.advance(PistonChain.Execution.Stage.CRYSTAL, now);
                     break;
                  case CRYSTAL:
                     if (this.bothReady(world) && world.at(this.plan.base()).kind() == PistonChain.Kind.BASE) {
                        if (!PistonChain.near(world.crystalPosition(), this.plan.start())) {
                           if (world.crystalPosition() == null && world.at(this.plan.crystal()).kind() == PistonChain.Kind.AIR) {
                              return this.sent ? PistonChain.Action.WAIT : PistonChain.Action.CRYSTAL;
                           }

                           return this.fail();
                        }

                        this.advance(PistonChain.Execution.Stage.FIRST_POWER, now);
                        break;
                     }

                     return this.fail();
                  case FIRST_POWER:
                     if (world.at(this.plan.firstPower()).kind() != PistonChain.Kind.REDSTONE) {
                        if (this.sent || PistonChain.near(world.crystalPosition(), this.plan.start()) && this.bothReady(world)) {
                           return this.blockPlacement(world, this.plan.firstPower(), PistonChain.Action.FIRST_POWER);
                        }

                        return this.fail();
                     }

                     this.advance(PistonChain.Execution.Stage.FIRST_PUSH, now);
                     break;
                  case FIRST_PUSH:
                     if (world.crystalPosition() == null) {
                        return this.fail();
                     }

                     if (world.at(this.plan.firstPiston()).piston(this.plan.firstPush(), true)
                        && world.at(this.plan.crystal()).kind() == PistonChain.Kind.HEAD
                        && world.at(this.plan.crystal()).facing() == this.plan.firstPush()
                        && PistonChain.near(world.crystalPosition(), this.plan.intermediate())) {
                        this.advance(PistonChain.Execution.Stage.REMOVE_PISTON, now);
                        break;
                     }

                     return PistonChain.Action.WAIT;
                  case REMOVE_PISTON:
                     if (!PistonChain.near(world.crystalPosition(), this.plan.intermediate())) {
                        return this.fail();
                     }

                     if (world.at(this.plan.firstPiston()).kind() != PistonChain.Kind.AIR) {
                        if (!world.at(this.plan.firstPiston()).piston(this.plan.firstPush(), true)) {
                           return this.fail();
                        }

                        return !this.sent && world.canBreakPiston(this.plan.firstPiston()) ? PistonChain.Action.REMOVE_FIRST_PISTON : PistonChain.Action.WAIT;
                     }

                     this.advance(PistonChain.Execution.Stage.CLEAR_HEAD, now);
                     break;
                  case CLEAR_HEAD:
                     if (!PistonChain.near(world.crystalPosition(), this.plan.intermediate())) {
                        return this.fail();
                     }

                     if (world.at(this.plan.firstPiston()).kind() == PistonChain.Kind.AIR && world.at(this.plan.crystal()).kind() == PistonChain.Kind.AIR) {
                        this.advance(PistonChain.Execution.Stage.SECOND_POWER, now);
                        break;
                     }

                     return PistonChain.Action.WAIT;
                  case SECOND_POWER:
                     if (world.at(this.plan.secondPower()).kind() != PistonChain.Kind.REDSTONE) {
                        if (this.sent
                           || PistonChain.near(world.crystalPosition(), this.plan.intermediate())
                              && world.at(this.plan.firstPiston()).kind() == PistonChain.Kind.AIR
                              && this.unpowered(world, this.plan.secondPiston(), this.plan.secondPush())
                              && world.at(this.plan.crystal()).kind() == PistonChain.Kind.AIR) {
                           return this.blockPlacement(world, this.plan.secondPower(), PistonChain.Action.SECOND_POWER);
                        }

                        return this.fail();
                     }

                     this.advance(PistonChain.Execution.Stage.SECOND_PUSH, now);
                     break;
                  case SECOND_PUSH:
                     if (world.crystalPosition() == null) {
                        return this.fail();
                     }

                     if (world.at(this.plan.secondPiston()).piston(this.plan.secondPush(), true) && PistonChain.near(world.crystalPosition(), this.plan.end())) {
                        this.stage = PistonChain.Execution.Stage.DONE;
                        return PistonChain.Action.DONE;
                     }

                     return PistonChain.Action.WAIT;
                  default:
                     return this.fail();
               }
            }
         }
      }

      private boolean bothReady(PistonChain.World world) {
         return this.unpowered(world, this.plan.firstPiston(), this.plan.firstPush())
            && this.unpowered(world, this.plan.secondPiston(), this.plan.secondPush());
      }

      private boolean unpowered(PistonChain.World world, BlockPos pos, Direction direction) {
         return world.at(pos).piston(direction, false) && !world.powered(pos, direction);
      }

      private PistonChain.Action pistonPlacement(PistonChain.World world, BlockPos pos, Direction facing, PistonChain.Action action) {
         if (world.at(pos).kind() == PistonChain.Kind.MOVING) {
            return PistonChain.Action.WAIT;
         } else {
            return world.powered(pos, facing) ? this.fail() : this.blockPlacement(world, pos, action);
         }
      }

      private PistonChain.Action blockPlacement(PistonChain.World world, BlockPos pos, PistonChain.Action action) {
         if (world.at(pos).kind() != PistonChain.Kind.AIR) {
            return this.fail();
         } else {
            return !this.sent && world.canPlace(pos) ? action : PistonChain.Action.WAIT;
         }
      }

      void sent(PistonChain.Action action, long now) {
         PistonChain.Action expected = switch (this.stage) {
            case FIRST -> PistonChain.Action.FIRST_PISTON;
            case SECOND -> PistonChain.Action.SECOND_PISTON;
            case BASE -> PistonChain.Action.BASE;
            case CRYSTAL -> PistonChain.Action.CRYSTAL;
            case FIRST_POWER -> PistonChain.Action.FIRST_POWER;
            default -> null;
            case REMOVE_PISTON -> PistonChain.Action.REMOVE_FIRST_PISTON;
            case SECOND_POWER -> PistonChain.Action.SECOND_POWER;
         };
         if (!this.sent && action == expected) {
            this.sent = true;
            this.deadline = now + this.stageTimeout();
         } else {
            throw new IllegalStateException("No matching pending chain action");
         }
      }

      private void advance(PistonChain.Execution.Stage next, long now) {
         this.stage = next;
         this.sent = false;
         this.deadline = now + this.stageTimeout();
      }

      private long stageTimeout() {
         return this.stage == PistonChain.Execution.Stage.REMOVE_PISTON ? 30000L + this.timeoutMs : this.timeoutMs;
      }

      private PistonChain.Action fail() {
         this.stage = PistonChain.Execution.Stage.FAILED;
         return PistonChain.Action.FAILED;
      }

      private static enum Stage {
         FIRST,
         SECOND,
         BASE,
         CRYSTAL,
         FIRST_POWER,
         FIRST_PUSH,
         REMOVE_PISTON,
         CLEAR_HEAD,
         SECOND_POWER,
         SECOND_PUSH,
         DONE,
         FAILED;

         private Stage() {
         }
      }
   }

   static enum Kind {
      AIR,
      PISTON,
      HEAD,
      MOVING,
      REDSTONE,
      BASE,
      BLOCKED;

      private Kind() {
      }
   }

   record Plan(
      BlockPos firstPiston,
      Direction firstPush,
      BlockPos firstPower,
      BlockPos secondPiston,
      Direction secondPush,
      BlockPos secondPower,
      BlockPos crystal,
      BlockPos base,
      Vec3 start,
      Vec3 intermediate,
      Vec3 end
   ) {
      Plan(
         BlockPos firstPiston,
         Direction firstPush,
         BlockPos firstPower,
         BlockPos secondPiston,
         Direction secondPush,
         BlockPos secondPower,
         BlockPos crystal,
         BlockPos base,
         Vec3 start,
         Vec3 intermediate,
         Vec3 end
      ) {
         firstPiston = firstPiston.immutable();
         firstPower = firstPower.immutable();
         secondPiston = secondPiston.immutable();
         secondPower = secondPower.immutable();
         crystal = crystal.immutable();
         base = base.immutable();
         this.firstPiston = firstPiston;
         this.firstPush = firstPush;
         this.firstPower = firstPower;
         this.secondPiston = secondPiston;
         this.secondPush = secondPush;
         this.secondPower = secondPower;
         this.crystal = crystal;
         this.base = base;
         this.start = start;
         this.intermediate = intermediate;
         this.end = end;
      }

      Set<BlockPos> blocks() {
         return Set.of(this.firstPiston, this.firstPower, this.secondPiston, this.secondPower, this.base);
      }
   }

   interface World {
      PistonChain.Cell at(BlockPos var1);

      boolean canPlace(BlockPos var1);

      boolean canBreakPiston(BlockPos var1);

      boolean powered(BlockPos var1, Direction var2);

      Vec3 crystalPosition();

      Vec3 collide(AABB var1, Vec3 var2, Set<BlockPos> var3);

      double placementCost(BlockPos var1);
   }
}
