package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import uk.ac.bris.cs.scotlandyard.model.Piece.MrX;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.Move.SingleMove;
import uk.ac.bris.cs.scotlandyard.model.Move.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;

import java.util.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	@Nonnull @Override public GameState build(
		GameSetup setup,
		Player mrX,
		ImmutableList<Player> detectives) {
		return new MyGameState(setup, mrX, detectives);
	}

	private static final class MyGameState implements GameState {
		private final GameSetup setup;
		private final ImmutableSet<Piece> remaining; // 剩余玩家
		private final ImmutableList<LogEntry> log; // MrX 的旅行日志
		private final ImmutableSet<Move> moves; // 当前回合的可用移动
		private final ImmutableSet<Piece> winner; // 当前游戏的胜者

		private final int currentRound; // 当前回合
		private final Player mrXPlayer;
		private final ImmutableList<Player> detectivePlayers;


		private MyGameState(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
			if (setup == null || mrX == null || detectives == null)
				throw new NullPointerException("NullPointer");
			if (setup.moves.isEmpty() || setup.graph.nodes().isEmpty())
				throw new IllegalArgumentException("IllegalArgument");

			Set<Integer> detectiveLocations = new HashSet<>();
			for (Player detective : detectives) {
				if (detective.has(ScotlandYard.Ticket.DOUBLE) || detective.has(ScotlandYard.Ticket.SECRET)) {
					throw new IllegalArgumentException("IllegalArgument");
				}
				if (!detectiveLocations.add(detective.location())) {
					throw new IllegalArgumentException("IllegalArgument");
				}
			}

			this.setup = setup;
			this.mrXPlayer = mrX;
			this.detectivePlayers = detectives;

			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
			builder.add(mrXPlayer.piece());
			for (Player d : detectivePlayers) {
				builder.add(d.piece());
			}
			this.remaining = builder.build();

			this.log = ImmutableList.of();    // 初始情况下，日志为空
			this.moves = calculateAvailableMoves();  // 根据当前状态计算
			this.winner = ImmutableSet.of();         // 初始没有胜者
			this.currentRound = 0;
		}

		private MyGameState(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives,
			int currentRound,
			ImmutableList<LogEntry> log,
			ImmutableSet<Piece> winner) {

			// 参数检查
			if (setup == null || mrX == null || detectives == null)
				throw new NullPointerException("NullPointer in MyGameState constructor");
			if (setup.moves.isEmpty() || setup.graph.nodes().isEmpty())
				throw new IllegalArgumentException("Invalid setup: empty moves or graph");
			Set<Integer> detectiveLocations = new HashSet<>();
			for (Player d : detectives) {
				if (d.has(Ticket.DOUBLE) || d.has(Ticket.SECRET)) {
					throw new IllegalArgumentException("Detectives cannot hold DOUBLE or SECRET tickets");
				}
				if (!detectiveLocations.add(d.location())) {
					throw new IllegalArgumentException("Two detectives in same location!");
				}
			}

			this.setup = setup;
			this.mrXPlayer = mrX;
			this.detectivePlayers = detectives;
			this.currentRound = currentRound;
			this.log = log;
			this.winner = winner;

			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
			builder.add(mrXPlayer.piece());
			for (Player d : detectivePlayers) builder.add(d.piece());
			this.remaining = builder.build();

			if (!winner.isEmpty()) {
				this.moves = ImmutableSet.of();
			} else {
				this.moves = calculateAvailableMoves();
			}
		}

		// 返回当前游戏设置
		@Nonnull @Override
		public GameSetup getSetup() {
			return setup;
		}

		// 返回所有玩家
		@Nonnull @Override
		public ImmutableSet<Piece> getPlayers() {
			return remaining;
		}

		// 返回某个侦探的位置
		@Nonnull @Override
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			for (Player player : detectivePlayers) {
				if (player.piece().equals(detective)) {
					return Optional.of(player.location());
				}
			}
			return Optional.empty();
		}

		// 返回 MrX 的旅行日志
		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		// 返回当前可用的移动
		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			return moves;
		}

		// 返回游戏的胜者
		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return winner;
		}


		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			// 先找对应的 Player
			Player player = findPlayer(piece);
			if (player == null) return Optional.empty();
			return Optional.of(ticket -> player.tickets().getOrDefault(ticket, 0));
		}

		// 处理玩家的移动并返回新的游戏状态
		@Nonnull
		@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) {
				throw new IllegalArgumentException("Invalid move");
			}
			if (move.commencedBy().isMrX()) {
				return handleMrXMove(move);
			} else {
				return handleDetectiveMove(move);
			}
		}

		// ============ 辅助方法 ============
		private Player findPlayer(Piece piece) {
			if (mrXPlayer.piece().equals(piece)) {
				return mrXPlayer;
			}
			for (Player d : detectivePlayers) {
				if (d.piece().equals(piece)) {
					return d;
				}
			}
			return null;
		}

		private ImmutableSet<Move> calculateAvailableMoves() {
			ImmutableSet.Builder<Move> availableMoves = ImmutableSet.builder();

			// 如果是 Mr. X，计算其可以进行的单步或双步移动
			if (mrXPlayer.piece().isMrX()) {
				// 获取 Mr. X 当前的票务信息
				ImmutableMap<Ticket, Integer> mrXTickets = mrXPlayer.tickets();

				// 计算 Mr. X 的单步移动
				for (int destination : setup.graph.adjacentNodes(mrXPlayer.location())) {
					for (Transport transport : setup.graph.edgeValueOrDefault(mrXPlayer.location(), destination, ImmutableSet.of())) {
						Ticket ticketRequired = transport.requiredTicket();

						// 判断 Mr. X 是否有足够的票
						if (mrXTickets.getOrDefault(ticketRequired, 0) > 0) {
							availableMoves.add(new SingleMove(mrXPlayer.piece(), mrXPlayer.location(), ticketRequired, destination));
						}
					}
				}

				// 计算 Mr. X 的双步移动（如果他有 Double Ticket）
				if (mrXTickets.getOrDefault(Ticket.DOUBLE, 0) > 0) {
					for (int firstDestination : setup.graph.adjacentNodes(mrXPlayer.location())) {
						for (Transport firstTransport : setup.graph.edgeValueOrDefault(mrXPlayer.location(), firstDestination, ImmutableSet.of())) {
							Ticket firstTicketRequired = firstTransport.requiredTicket();

							if (mrXTickets.getOrDefault(firstTicketRequired, 0) > 0) {
								for (int secondDestination : setup.graph.adjacentNodes(firstDestination)) {
									for (Transport secondTransport : setup.graph.edgeValueOrDefault(firstDestination, secondDestination, ImmutableSet.of())) {
										Ticket secondTicketRequired = secondTransport.requiredTicket();

										// 判断 Mr. X 是否有足够的票进行双步移动
										if (mrXTickets.getOrDefault(secondTicketRequired, 0) > 0) {
											availableMoves.add(new DoubleMove(mrXPlayer.piece(), mrXPlayer.location(),
												firstTicketRequired, firstDestination, secondTicketRequired, secondDestination));
										}
									}
								}
							}
						}
					}
				}
			}

			// 如果是侦探，计算其可以移动到的合法位置
			for (Player detective : detectivePlayers) {
				// 获取侦探的票务信息
				ImmutableMap<Ticket, Integer> detectiveTickets = detective.tickets();

				// 计算侦探的单步移动
				for (int destination : setup.graph.adjacentNodes(detective.location())) {
					for (Transport transport : setup.graph.edgeValueOrDefault(detective.location(), destination, ImmutableSet.of())) {
						Ticket ticketRequired = transport.requiredTicket();

						// 判断侦探是否有足够的票进行移动
						if (detectiveTickets.getOrDefault(ticketRequired, 0) > 0) {
							availableMoves.add(new SingleMove(detective.piece(), detective.location(), ticketRequired, destination));
						}
					}
				}
			}

			return availableMoves.build();
		}



		private MyGameState nextState(
			Player newMrX,
			ImmutableList<Player> newDetectives,
			int nextRound,
			ImmutableList<LogEntry> newLog,
			ImmutableSet<Piece> newWinner) {

			return new MyGameState(
				this.setup,
				newMrX,
				newDetectives,
				nextRound,
				newLog,
				newWinner
			);
		}

		private GameState handleMrXMove(Move move) {
			Player currentMrX = this.mrXPlayer;
			return move.accept(new Move.Visitor<GameState>() {
				@Override
				public GameState visit(SingleMove m) {
					return doMrXSingleMove(m);
				}
				@Override
				public GameState visit(DoubleMove m) {
					return doMrXDoubleMove(m);
				}
			});
		}


		private GameState doMrXSingleMove(SingleMove m) {
			// 扣除对应票务
			Player updatedMrX = mrXPlayer.use(m.ticket);
			// 移动位置
			updatedMrX = updatedMrX.at(m.destination);


			// 揭示规则
			boolean reveal = setup.moves.get(currentRound);
			ImmutableList<LogEntry> updatedLog = updateLogForMrX(log, m.ticket, m.destination, reveal);

			// 侦探胜利
			if (detectiveInLocation(updatedMrX.location())) {
				return nextState(
					updatedMrX,
					detectivePlayers,
					currentRound + 1,
					updatedLog,
					detectivesWin()
				);
			}

			// MrX 胜利
			if (updatedLog.size() >= setup.moves.size()) {
				// MrX 走完了全部行程
				return nextState(
					updatedMrX,
					detectivePlayers,
					currentRound + 1,
					updatedLog,
					mrXWin()
				);
			}

			// 游戏继续
			return nextState(
				updatedMrX,
				detectivePlayers,
				currentRound + 1,
				updatedLog,
				ImmutableSet.of()
			);
		}

		private GameState doMrXDoubleMove(DoubleMove m) {
			// 扣除票务：m.ticket1, m.ticket2, 以及 DOUBLE
			Player updatedMrX = mrXPlayer.use(m.ticket1).use(m.ticket2).use(Ticket.DOUBLE);

			// 第一段移动
			updatedMrX = updatedMrX.at(m.destination1);

			// 侦探是否在 destination1
			if (detectiveInLocation(updatedMrX.location())) {
				// 侦探抓到 MrX
				ImmutableList<LogEntry> logAfterFirst = updateLogForMrX(log, m.ticket1, m.destination1, setup.moves.get(currentRound));
				return nextState(
					updatedMrX,
					detectivePlayers,
					currentRound + 1,
					logAfterFirst,
					detectivesWin()
				);
			}

			// 更新日志
			boolean reveal = setup.moves.get(currentRound);
			ImmutableList<LogEntry> logAfterFirst = updateLogForMrX(log, m.ticket1, m.destination1, reveal);

			updatedMrX = updatedMrX.at(m.destination2);
			ImmutableList<LogEntry> finalLog = updateLogForMrX(logAfterFirst, m.ticket2, m.destination2, reveal);

			// 侦探在 destination2, 侦探获胜
			if (detectiveInLocation(updatedMrX.location())) {
				return nextState(
					updatedMrX,
					detectivePlayers,
					currentRound + 1,
					finalLog,
					detectivesWin()
				);
			}

			// 若 MrX 日志已达最大, MrX 胜利
			if (finalLog.size() >= setup.moves.size()) {
				return nextState(
					updatedMrX,
					detectivePlayers,
					currentRound + 1,
					finalLog,
					mrXWin()
				);
			}


			return nextState(
				updatedMrX,
				detectivePlayers,
				currentRound + 1,
				finalLog,
				ImmutableSet.of()
			);
		}

		private GameState handleDetectiveMove(Move move) {
			// 找到执行移动的侦探 Player
			Player detective = findPlayer(move.commencedBy());
			if (detective == null) throw new IllegalStateException("Cannot find detective player");

			return move.accept(new Move.Visitor<GameState>() {
				@Override
				public GameState visit(SingleMove m) {
					return doDetectiveSingleMove(detective, m);
				}

				@Override
				public GameState visit(DoubleMove m) {
					throw new IllegalArgumentException("Detective cannot double-move");
				}
			});
		}

		private GameState doDetectiveSingleMove(Player detective, SingleMove m) {
			//  扣侦探的票
			Player updatedDetective = detective.use(m.ticket);
			//  侦探移动到 destination
			updatedDetective = updatedDetective.at(m.destination);
			//  侦探用掉的票给 MrX
			Player updatedMrX = mrXPlayer.give(m.ticket);

			// 替换该侦探
			List<Player> newDetectives = new ArrayList<>(detectivePlayers);
			for (int i = 0; i < newDetectives.size(); i++) {
				if (newDetectives.get(i).piece().equals(detective.piece())) {
					newDetectives.set(i, updatedDetective);
					break;
				}
			}
			ImmutableList<Player> updatedDetectives = ImmutableList.copyOf(newDetectives);

			// 若侦探移动到 MrX 的位置 => 侦探获胜
			if (updatedDetective.location() == mrXPlayer.location()) {
				return nextState(
					updatedMrX,
					updatedDetectives,
					currentRound,
					log,
					detectivesWin()
				);
			}

			// 判断游戏是否结束:
			return nextState(
				updatedMrX,
				updatedDetectives,
				currentRound,
				log,
				ImmutableSet.of()
			);
		}


		private boolean detectiveInLocation(int location) {
			for (Player d : detectivePlayers) {
				if (d.location() == location) return true;
			}
			return false;
		}

		private ImmutableList<LogEntry> updateLogForMrX(ImmutableList<LogEntry> oldLog, Ticket t, int location, boolean reveal) {
			List<LogEntry> newLog = new ArrayList<>(oldLog);
			if (reveal) {
				newLog.add(LogEntry.reveal(t, location));
			} else {
				newLog.add(LogEntry.hidden(t));
			}
			return ImmutableList.copyOf(newLog);
		}

		private ImmutableSet<Piece> detectivesWin() {
			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
			for (Player d : detectivePlayers) {
				builder.add(d.piece());
			}
			return builder.build();
		}


		private ImmutableSet<Piece> mrXWin() {
			return ImmutableSet.of(mrXPlayer.piece());
		}


	}
}


