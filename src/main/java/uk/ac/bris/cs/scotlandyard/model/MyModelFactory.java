
package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;



public final class MyModelFactory implements Factory<Model> {

	@Nonnull
	@Override
	public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyModel(new MyGameStateFactory().build(setup, mrX, detectives));
	}

	private static final class MyModel implements Model {
		private final List<Observer> observers = new ArrayList<>();
		private GameState gameState;

		public MyModel(GameState initialState) {
			this.gameState = initialState;
		}

		@Override
		public Board getCurrentBoard() {
			return gameState; //  `GameState` 继承 `Board`，可以直接返回
		}

		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer cannot be null");
			if (observers.contains(observer)) throw new IllegalArgumentException("Observer already registered");
			observers.add(observer);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer cannot be null");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Observer not found");
			observers.remove(observer);
		}

		@Override
		public ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observers);
		}



		@Override
		public void chooseMove(@Nonnull Move move) {
			gameState = gameState.advance(move);

			// 判断游戏是否结束
			Model.Observer.Event event = gameState.getWinner().isEmpty()
					? Model.Observer.Event.MOVE_MADE
					: Model.Observer.Event.GAME_OVER;

			// 通知所有 Observer
			for (Observer observer : observers) {
				observer.onModelChanged(gameState, event);
			}
		}
	}

}

