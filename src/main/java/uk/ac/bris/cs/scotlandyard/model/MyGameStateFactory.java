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
		private final ImmutableSet<Piece> remaining;
		private final ImmutableList<LogEntry> log; // MrX 的旅行日志

		ImmutableMap<Piece, ImmutableSet<Move>> movesMap;
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
			this.remaining = ImmutableSet.of(mrX.piece());

			this.currentRound = 0;
			this.log = ImmutableList.of();    // 初始情况下，日志为空
			this.movesMap  = buildMovesMap();

			this.winner = calculateWinner(this);  // 根据条件计算胜利者
			if(!winner.isEmpty()){
				System.out.println("mrX win in init step");
				this.movesMap  = ImmutableMap.of();
			}


		}

		private MyGameState(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives,
			int currentRound,
			ImmutableList<LogEntry> log,
			ImmutableSet<Piece> winner,
			ImmutableSet<Piece> remaining
		) {

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
			this.remaining = remaining;

			if (!winner.isEmpty()) {
				this.movesMap  = ImmutableMap.of();
			} else {
				this.movesMap  = buildMovesMap();
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
			if (!winner.isEmpty()) return ImmutableSet.of();

			if (mrXTurn()) {
				return movesMap.getOrDefault(mrXPlayer.piece(), ImmutableSet.of());
			}else{
				return getdetectivesMove();
			}
		}

		ImmutableSet<Move> getdetectivesMove(){
			ImmutableSet.Builder<Move> combined = ImmutableSet.builder();
			for (Player detective : detectivePlayers) {
				if (remaining.contains(detective.piece())) {
					combined.addAll(movesMap.getOrDefault(detective.piece(), ImmutableSet.of()));
				}
			}
			return combined.build();
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
			if (move.commencedBy().isMrX()) {
				return handleMrXMove(move);
			} else {
				return handleDetectiveMove(move);
			}
		}

		// ============ 辅助方法 ============


		private ImmutableMap<Piece, ImmutableSet<Move>> buildMovesMap() {
			ImmutableMap.Builder<Piece, ImmutableSet<Move>> builder = ImmutableMap.builder();

			// MrX 的所有可用移动
			builder.put(mrXPlayer.piece(), calculateAvailableMovesForMrX(this));

			// 每个侦探分开计算可用移动
			for (Player detective : detectivePlayers) {
				if(!remaining.contains(detective.piece()))
					continue;
				builder.put(detective.piece(), calculateAvailableMovesForDetective(detective));
			}

			return builder.build();
		}

		private ImmutableSet<Move> calculateAvailableMovesForMrX(MyGameState myGameState) {
			ImmutableSet.Builder<Move> availableMoves = ImmutableSet.builder();

			// 获取 Mr. X 当前的票务信息
			ImmutableMap<Ticket, Integer> mrXTickets = myGameState.mrXPlayer.tickets();

			// 计算 Mr. X 的单步移动
			for (int destination : myGameState.setup.graph.adjacentNodes(myGameState.mrXPlayer.location())) {
				if (detectiveInLocation(destination)) {
					continue; // 如果目标位置已被侦探占用，跳过该目标位置
				}
				for (Transport transport : myGameState.setup.graph.edgeValueOrDefault(myGameState.mrXPlayer.location(), destination, ImmutableSet.of())) {
					Ticket ticketRequired = transport.requiredTicket();

					// 判断 Mr. X 是否有足够的票
					if (mrXTickets.getOrDefault(ticketRequired, 0) > 0) {
						availableMoves.add(new SingleMove(myGameState.mrXPlayer.piece(), myGameState.mrXPlayer.location(), ticketRequired, destination));
					}
					if (mrXTickets.getOrDefault(Ticket.SECRET, 0) > 0) {
						availableMoves.add(new SingleMove(
							myGameState.mrXPlayer.piece(),
							myGameState.mrXPlayer.location(),
							Ticket.SECRET,
							destination
						));
					}
				}
			}

			int remainingRounds = myGameState.setup.moves.size() - myGameState.currentRound;
			// 计算 Mr. X 的双步移动（如果他有 Double Ticket）
			if (mrXTickets.getOrDefault(Ticket.DOUBLE, 0) > 0 && remainingRounds >= 2)  {
				for (int firstDestination : myGameState.setup.graph.adjacentNodes(myGameState.mrXPlayer.location())) {
					if (detectiveInLocation(firstDestination)) continue;

					for (Transport firstTransport : myGameState.setup.graph.edgeValueOrDefault(myGameState.mrXPlayer.location(), firstDestination, ImmutableSet.of())) {
						Ticket firstTicketRequired = firstTransport.requiredTicket();
						List<Ticket> firstStepTickets = new ArrayList<>();

						if (mrXTickets.getOrDefault(firstTicketRequired, 0) > 0) {
							firstStepTickets.add(firstTicketRequired);
						}
						if (mrXTickets.getOrDefault(Ticket.SECRET, 0) > 0) {
							firstStepTickets.add(Ticket.SECRET);
						}

						if (firstStepTickets.isEmpty()) continue;
						for (int secondDestination : myGameState.setup.graph.adjacentNodes(firstDestination)) {
							if (detectiveInLocation(secondDestination)) continue;
							for (Transport secondTransport : myGameState.setup.graph.edgeValueOrDefault(firstDestination, secondDestination, ImmutableSet.of())) {
								Ticket secondRequired = secondTransport.requiredTicket();

								List<Ticket> secondStepTickets = new ArrayList<>();

								if (mrXTickets.getOrDefault(secondRequired, 0) > 0) {
									secondStepTickets.add(secondRequired);
								}

								if (mrXTickets.getOrDefault(Ticket.SECRET, 0) > 0) {
									secondStepTickets.add(Ticket.SECRET);
								}

								for (Ticket t1 : firstStepTickets) {
									for (Ticket t2 : secondStepTickets) {
										int secretNeeded = 0;
										if (t1 == Ticket.SECRET) secretNeeded++;
										if (t2 == Ticket.SECRET) secretNeeded++;

										if (t1 == t2 && mrXTickets.getOrDefault(t1, 0) < 2)
											continue;

										if (secretNeeded <= mrXTickets.getOrDefault(Ticket.SECRET, 0)) {
											availableMoves.add(new DoubleMove(
												myGameState.mrXPlayer.piece(),
												myGameState.mrXPlayer.location(),
												t1, firstDestination,
												t2, secondDestination
											));
										}

									}

								}
							}

						}




					}
				}
			}

			return availableMoves.build();
		}

		private ImmutableSet<Move> calculateAvailableMovesForDetective(Player detective) {
			ImmutableSet.Builder<Move> moves = ImmutableSet.builder();

			// 已有胜利者，则返回空集
			if (!winner.isEmpty()) {
				return ImmutableSet.of();
			}

			ImmutableMap<Ticket, Integer> tickets = detective.tickets();
			int source = detective.location();

			// 单步移动
			for (int destination : setup.graph.adjacentNodes(source)) {
				// 不得进入其他侦探所在位置
				boolean isOccupiedByOtherDetective = false;
				for (Player otherDetective : detectivePlayers) {
					if (otherDetective != detective && otherDetective.location() == destination) {
						isOccupiedByOtherDetective = true;
						break;
					}
				}

				if (isOccupiedByOtherDetective) {
					continue;
				}

				// 判断侦探是否有足够的票
				for (Transport transport : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
					Ticket required = transport.requiredTicket();
					if (tickets.getOrDefault(required, 0) > 0) {
						moves.add(new SingleMove(detective.piece(), source, required, destination));
					}
				}
			}

			return moves.build();
		}

		private ImmutableSet<Move> calculateAvailableMovesForDetectives(MyGameState myGameState) {
			ImmutableSet.Builder<Move> availableMoves = ImmutableSet.builder();

			// 如果是侦探，计算其可以移动到的合法位置
			for (Player detective : myGameState.detectivePlayers) {
				if (!myGameState.remaining.contains(detective.piece())) {
					continue;
				}
				// 获取侦探的票务信息
				ImmutableMap<Ticket, Integer> detectiveTickets = detective.tickets();

				// 计算侦探的单步移动
				for (int destination : myGameState.setup.graph.adjacentNodes(detective.location())) {
					boolean isOccupiedByOtherDetective = false;
					for (Player otherDetective : myGameState.detectivePlayers) {
						if (otherDetective != detective && otherDetective.location() == destination) {
							isOccupiedByOtherDetective = true;
							break;
						}
					}

					if (isOccupiedByOtherDetective) {
						continue;  // 如果目标位置被其他侦探占用，跳过该目标位置
					}

					for (Transport transport : myGameState.setup.graph.edgeValueOrDefault(detective.location(), destination, ImmutableSet.of())) {
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

		private ImmutableSet<Move> calculateAvailableMovesForDetectivesWithoutRemaining(MyGameState myGameState){
			ImmutableSet.Builder<Move> availableMoves = ImmutableSet.builder();

			// 如果是侦探，计算其可以移动到的合法位置
			for (Player detective : myGameState.detectivePlayers) {
				// 获取侦探的票务信息
				ImmutableMap<Ticket, Integer> detectiveTickets = detective.tickets();

				// 计算侦探的单步移动
				for (int destination : myGameState.setup.graph.adjacentNodes(detective.location())) {
					boolean isOccupiedByOtherDetective = false;
					for (Player otherDetective : myGameState.detectivePlayers) {
						if (otherDetective != detective && otherDetective.location() == destination) {
							isOccupiedByOtherDetective = true;
							break;
						}
					}

					if (isOccupiedByOtherDetective) {
						continue;  // 如果目标位置被其他侦探占用，跳过该目标位置
					}

					for (Transport transport : myGameState.setup.graph.edgeValueOrDefault(detective.location(), destination, ImmutableSet.of())) {
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

		private ImmutableSet<Piece> calculateWinner(MyGameState myGameState) {
			System.out.println("#" + myGameState);
			// 检查 MrX 是否被捕
			for (Player detective : myGameState.detectivePlayers) {
				if (detective.location() == myGameState.mrXPlayer.location()){
					System.out.println("detectives win cause of catch mrx");
					return detectivesWin();
				}

			}

			if(myGameState.log.size() >= myGameState.setup.moves.size()){
				return mrXWin();
			}

			boolean allDetectivesStuck = false;
			if (calculateAvailableMovesForDetectives(myGameState).isEmpty() && (!myGameState.mrXTurn())) {
				allDetectivesStuck = true;
			}
			if(allDetectivesOutOfTickets(myGameState)){
				allDetectivesStuck = true;
			}
			if(calculateAvailableMovesForDetectivesWithoutRemaining(myGameState).isEmpty() && myGameState.currentRound == 0){
				allDetectivesStuck = true;
			}


			boolean mrxStuck = false;
			if(calculateAvailableMovesForMrX(myGameState).isEmpty() && myGameState.mrXTurn())
				mrxStuck = true;

			if(isMrXBlocked(myGameState)){
				mrxStuck = true;
			}

			if(mrxStuck){
				System.out.println("detectives win cause of mrxStuck");
				System.out.println(myGameState);
				return detectivesWin();
			}

			if (allDetectivesStuck){
				System.out.println("mrXWin cause of allDetectivesStuck");
				System.out.println("extra output in calculateWinner:" + myGameState);
				return mrXWin();
			}


			// 如果没有胜利条件，则游戏继续，没有胜者
			return ImmutableSet.of();
		}

		private boolean allDetectivesOutOfTickets(MyGameState myGameState) {
			for (Player detective : myGameState.detectivePlayers) {
				if (detective.has(Ticket.TAXI) ||
					detective.has(Ticket.BUS) ||
					detective.has(Ticket.UNDERGROUND)) {
					return false;
				}
			}
			return true;
		}

		private boolean mrXTurn(){
			return currentRound % 2 == 0;
		}

		private boolean DetectiverTrun(){
			return currentRound % 2 == 1;
		}

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


		private MyGameState nextState(
			Player newMrX,
			ImmutableList<Player> newDetectives,
			int nextRound,
			ImmutableList<LogEntry> newLog,
			ImmutableSet<Piece> newWinner,
			ImmutableSet<Piece> newremaining) {


			ImmutableSet<Piece> filteredRemaining = newremaining.stream()
				.filter(p -> {
					Player detective = findPlayer(p);  // 获取对应的 Player
					if (detective != null && detective.piece().equals(p)) {
						// 如果侦探的可用路径为空，跳过这个侦探
						ImmutableSet<Move> availableMoves = movesMap.getOrDefault(detective.piece(), ImmutableSet.of());
						return !availableMoves.isEmpty();
					}
					return true;
				})
				.collect(ImmutableSet.toImmutableSet());


			if(DetectiverTrun()){
				if (filteredRemaining.isEmpty()) {
					nextRound++;
					filteredRemaining = ImmutableSet.of(newMrX.piece());
				}
			}else{
				nextRound++;
				ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
				for (Player d : detectivePlayers) builder.add(d.piece());
				filteredRemaining = builder.build();
			}


			MyGameState newState =  new MyGameState(
				this.setup,
				newMrX,
				newDetectives,
				nextRound,
				newLog,
				newWinner,
				filteredRemaining
			);

			ImmutableSet<Piece> winner = calculateWinner(newState);
			if(winner.isEmpty())
				return newState;
			else{
				return new MyGameState(
					this.setup,
					newMrX,
					newDetectives,
					nextRound,
					newLog,
					winner,
					filteredRemaining
				);
			}


		}

		private GameState handleMrXMove(Move move) {
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

		private boolean isMrXBlocked(MyGameState myGameState) {
			int mrXLocation = myGameState.mrXPlayer.location();

			// 获取 MRX 可以去的所有邻接节点
			Set<Integer> possibleDestinations = myGameState.setup.graph.adjacentNodes(mrXLocation);

			// 检查所有邻接节点是否都被侦探占据
			for (int destination : possibleDestinations) {
				if (!isOccupiedByDetective(myGameState, destination)) {
					return false;
				}
			}

			return true;
		}


		private boolean isOccupiedByDetective(MyGameState myGameState, int location) {
			for (Player detective : myGameState.detectivePlayers) {
				if (detective.location() == location) return true;
			}
			return false;
		}

		private GameState doMrXSingleMove(SingleMove m) {
			// 扣除对应票务
			Player updatedMrX = mrXPlayer.use(m.ticket);
			// 移动位置
			updatedMrX = updatedMrX.at(m.destination);


			// 揭示规则
			boolean reveal = setup.moves.get(currentRound);
			ImmutableList<LogEntry> updatedLog = updateLogForMrX(log, m.ticket, m.destination, reveal);

			// MrX 胜利
			if (updatedLog.size() >= setup.moves.size()) {
				// MrX 走完了全部行程
				System.out.println("mrXWin cause of MrX get maxPath");
				return new MyGameState(
					setup,
					updatedMrX,
					detectivePlayers,
					currentRound,
					updatedLog,
					mrXWin(),
					remaining
				);
			}

			// 游戏继续
			return nextState(
				updatedMrX,
				detectivePlayers,
				currentRound,
				updatedLog,
				ImmutableSet.of(),
				remaining
			);
		}

		private GameState doMrXDoubleMove(DoubleMove m) {
			// 扣除票务：m.ticket1, m.ticket2, 以及 DOUBLE
			Player updatedMrX = mrXPlayer.use(m.ticket1).use(m.ticket2).use(Ticket.DOUBLE);

			// 第一段移动
			updatedMrX = updatedMrX.at(m.destination1);

			// 更新日志
			boolean reveal = setup.moves.get(currentRound);
			ImmutableList<LogEntry> logAfterFirst = updateLogForMrX(log, m.ticket1, m.destination1, reveal);

			updatedMrX = updatedMrX.at(m.destination2);
			ImmutableList<LogEntry> finalLog = updateLogForMrX(logAfterFirst, m.ticket2, m.destination2, reveal);

			// 若 MrX 日志已达最大, MrX 胜利
			if (finalLog.size() >= setup.moves.size()) {
				System.out.println("mrXWin cause of MrX get maxPath");
				return new MyGameState(
					setup,
					updatedMrX,
					detectivePlayers,
					currentRound,
					finalLog,
					mrXWin(),
					remaining
				);
			}


			return nextState(
				updatedMrX,
				detectivePlayers,
				currentRound,
				finalLog,
				ImmutableSet.of(),
				remaining
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

			ImmutableSet<Piece> newRemaining = this.remaining.stream()
				.filter(p-> !p.equals(detective.piece())).collect(ImmutableSet.toImmutableSet());


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
				System.out.println("detectives win cause of catch mrx");
				return new MyGameState(
					setup,
					updatedMrX,
					updatedDetectives,
					currentRound,
					log,
					detectivesWin(),
					newRemaining
				);
			}

			// 判断游戏是否结束:
			return nextState(
				updatedMrX,
				updatedDetectives,
				currentRound,
				log,
				ImmutableSet.of(),
				newRemaining
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

		@Override
		public String toString() {
			return "MyGameState{" +
				"currentRound=" + currentRound +
				", mrXPlayer=" + mrXPlayer +
				", detectivePlayers=" + detectivePlayers +
				", log=" + log +
				", movesMap=" + movesMap +
				", winner=" + winner +
				", remaining=" + remaining +
				'}';
		}


	}
}
