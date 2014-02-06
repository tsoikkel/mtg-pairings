(ns mtg-pairings-server.tournaments
  (:require [korma.core :as sql]
            [mtg-pairings-server.sql-db :as db]
            [mtg-pairings-server.mtg-util :as mtg-util]
            [mtg-pairings-server.util :as util]
            [clojure.tools.reader.edn :as edn]))


(defn tournament [id]
  (first 
    (sql/select db/tournament
     (sql/where {:id id}))))

(defn tournaments []
  (let [tourns (sql/select db/tournament
                 (sql/order :day :DESC)
                 (sql/order :name :ASC)
                 (sql/with db/round
                   (sql/fields :num)
                   (sql/order :num)
                   (sql/where
                     (sql/sqlfn exists (sql/subselect db/pairing
                                         (sql/where {:round :round.id})))))
                 (sql/with db/standings
                   (sql/fields [:round :num])
                   (sql/order :round)))]
    (for [tournament tourns]
      (-> tournament 
        (update-in [:round] #(map :num %))
        (update-in [:standings] #(map :num %))))))

(defn player [dci]
  (first
    (sql/select db/player
      (sql/where {:dci dci}))))

(defn add-tournament [tourn]
  (let [tourn (sql/insert db/tournament
                (sql/values (select-keys tourn [:name :day :rounds])))]
    (:id tourn)))

(defn add-players [players]
  (let [old-players (->> (sql/select db/player)
                      (map :dci)
                      set)
        new-players (->> players
                      (remove #(old-players (:dci %)))
                      (map #(select-keys % [:name :dci])))]
    (when (seq new-players) 
      (sql/insert db/player
        (sql/values new-players)))))

(defn ^:private add-team [name tournament-id]
  (let [t (sql/insert db/team
            (sql/values {:name name
                         :tournament tournament-id}))]
    (:id t)))

(defn add-teams [tournament-id teams]
  (add-players (mapcat :players teams))
  (doseq [team teams
          :let [team-id (add-team (:name team) tournament-id)]]
    (sql/insert db/team-players
      (sql/values (for [player (:players team)]
                    {:team team-id
                     :player (:dci player)})))))

(defn standings [tournament-id round-num]
  (-> (sql/select db/standings
       (sql/where {:tournament tournament-id
                   :round round-num}))
      first
      :standings
      edn/read-string))

(defn ^:private add-round [tournament-id round-num]
  (let [round (sql/insert db/round 
                (sql/values {:tournament tournament-id
                             :num round-num}))]
    (:id round)))

(defn add-pairings [tournament-id round-num pairings]
  (let [teams (sql/select db/team
                (sql/where {:tournament tournament-id}))
        name->id (into {} (for [team teams]
                            [(:name team) (:id team)]))
        team->points (if-let [standings (standings tournament-id (dec round-num))]
                       (into {} (for [row standings]
                                  [(:team row) (:points row)]))
                       (constantly 0))
        round-id (add-round tournament-id round-num)]
    (sql/insert db/pairing
      (sql/values (for [pairing pairings
                        :let [team1 (name->id (:team1 pairing))
                              team2 (name->id (:team2 pairing))]]
                    {:round round-id
                     :team1 team1
                     :team2 team2
                     :team1_points (team->points team1)
                     :team2_points (team->points team2)})))))

(defn ^:private find-pairing [round-id team1 team2]
  (first (sql/select db/pairing
           (sql/where {:round round-id
                       :team1 team1
                       :team2 team2}))))

(defn ^:private results-of-round [round-id]
  (sql/select db/pairing
    (sql/fields [:team1 :team-1] 
                [:team2 :team-2])
    (sql/with db/team1
      (sql/fields [:name :team-1-name]))
    (sql/with db/team2
      (sql/fields [:name :team-2-name]))
    (sql/with db/result
      (sql/fields [:team1_wins :wins] 
                  [:team2_wins :losses]
                  :draws))
    (sql/where {:round round-id})))

(defn ^:private calculate-standings [tournament-id]
  (let [rounds (sql/select db/round
                 (sql/where {:tournament tournament-id})
                 (sql/order :num :DESC))
        rounds-results (into {} (for [r rounds]
                                  [(:num r) (results-of-round (:id r))]))
        round-num (:num (first rounds))
        round-id (:id (first rounds))
        _ (clojure.pprint/pprint rounds-results)
        std (mtg-util/calculate-standings rounds-results round-num)]
    (sql/insert db/standings 
      (sql/values {:standings (pr-str std)
                   :tournament tournament-id
                   :round round-id}))))

(defn add-results [tournament-id round-num results]
  (let [round-id (:id (first (sql/select db/round
                               (sql/where {:tournament tournament-id
                                           :num round-num}))))
        name->id (into {} (for [team (sql/select db/team
                                       (sql/where {:tournament tournament-id}))]
                            [(:name team) (:id team)]))]
    (doseq [res results
            :let [pairing-id (:id (find-pairing round-id 
                                                    (name->id (:team1 res))
                                                    (name->id (:team2 res))))]]
      (sql/insert db/result
        (sql/values {:pairing pairing-id
                     :team1_wins (:wins res)
                     :team2_wins (:losses res)
                     :draws (:draws res)})))
    (calculate-standings tournament-id)))