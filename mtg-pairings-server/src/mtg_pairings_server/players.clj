(ns mtg-pairings-server.players
  (:require [korma.core :as sql]
            [mtg-pairings-server.sql-db :as db]
            [mtg-pairings-server.util :refer [select-and-rename-keys]]))

(defn player [dci]
  (first
    (sql/select db/player
      (sql/where {:dci dci}))))

(defn players []
  (sql/select db/player))

(defn ^:private format-pairing
  [pairing team-id]
  (if (= team-id (:team1 pairing))
    (select-keys pairing [:round_number :team1_name :team2_name :team1_points :team2_points :table_number])
    (select-and-rename-keys pairing [:round_number [:team1_name :team2_name] [:team2_name :team1_name]
                                     :table_number [:team1_points :team2_points] [:team2_points :team1_points]])))

(defn ^:private add-players-data [tournament dci]
  (let [players-team (:id (first (sql/select db/team
                        (sql/where (and {:tournament (:id tournament)}
                                        (sql/sqlfn exists
                                          (sql/subselect db/team-players
                                            (sql/where {:team_players.player dci
                                                        :team_players.team :team.id}))))))))
        pairings (for [pairing (sql/select db/pairing
                                 (sql/with db/team1
                                   (sql/fields [:name :team1_name]))
                                 (sql/with db/team2
                                   (sql/fields [:name :team2_name]))
                                 (sql/with db/round
                                   (sql/fields [:num :round_number]))
                                 (sql/where (and {:round.tournament (:id tournament)}
                                                 (or {:team1 players-team}
                                                     {:team2 players-team})))
                                 (sql/order :round.num :DESC))]
                   (format-pairing pairing players-team))]
    (assoc tournament :pairings pairings)))

(defn tournaments [dci]
  (for [tournament (sql/select db/tournament
                     (sql/where (sql/sqlfn exists 
                                  (sql/subselect db/team-players
                                    (sql/join db/team (= :team.id :team_players.team))
                                    (sql/where {:team_players.player dci
                                                :team.tournament :tournament.id})))))]
    (add-players-data tournament dci)))