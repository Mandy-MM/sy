
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
<<<<<<< HEAD
		private GameState gameState;// current gamestate

		// set the initial game state.
=======
		private GameState gameState;

>>>>>>> 05664f83977be7299836f05f51ab03e7f0fdc65b
		public MyModel(GameState initialState) {
			this.gameState = initialState;
		}

<<<<<<< HEAD
		//Returns the current game board (GameState implements Board).
		@Override
		public Board getCurrentBoard() {
			return gameState;
		}

		// Registers a new observer
=======
		@Override
		public Board getCurrentBoard() {
			return gameState; //  `GameState` 继承 `Board`，可以直接返回
		}

>>>>>>> 05664f83977be7299836f05f51ab03e7f0fdc65b
		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer cannot be null");
			if (observers.contains(observer)) throw new IllegalArgumentException("Observer already registered");
			observers.add(observer);
		}

<<<<<<< HEAD
		//Unregisters an existing observer.
=======
>>>>>>> 05664f83977be7299836f05f51ab03e7f0fdc65b
		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer cannot be null");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Observer not found");
			observers.remove(observer);
		}

<<<<<<< HEAD
		//Return registered observers
=======
>>>>>>> 05664f83977be7299836f05f51ab03e7f0fdc65b
		@Override
		public ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observers);
		}



		@Override
		public void chooseMove(@Nonnull Move move) {
<<<<<<< HEAD
			gameState = gameState.advance(move);// new gamestate

			// Determine whether the game is over
			Model.Observer.Event event = gameState.getWinner().isEmpty()
				? Model.Observer.Event.MOVE_MADE
				: Model.Observer.Event.GAME_OVER;

			// Notify all registered observers
=======
			gameState = gameState.advance(move);

			// 判断游戏是否结束
			Model.Observer.Event event = gameState.getWinner().isEmpty()
					? Model.Observer.Event.MOVE_MADE
					: Model.Observer.Event.GAME_OVER;

			// 通知所有 Observer
>>>>>>> 05664f83977be7299836f05f51ab03e7f0fdc65b
			for (Observer observer : observers) {
				observer.onModelChanged(gameState, event);
			}
		}
	}

}

