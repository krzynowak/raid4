
public interface RAIDInterface {

	/**
	 * Typ wyliczeniowy reprezentujący stany macierzy dyskowej RAID.
	 */
	public enum RAIDState {
		UNKNOWN, INITIALIZATION, NORMAL, DEGRADED, REBUILD;
	}

	/**
	 * Metoda zwraca aktualny stan macierzy.
	 * 
	 * @return stan macierzy
	 */
	public RAIDState getState();

	/**
	 * Dodaje dyski "fizyczne" do macirzy RAID Metoda na pewno zostanie wykonana
	 * przed innymi metodami tego samego interfejsu. Kolejność dodawanych dysków
	 * jest wiążąca. Ostatni z dodanych dysków przeznaczony jest na dane nadmiarowe.
	 * 
	 * @param disk dysk do użycia do budowy macierzy
	 */
	public void addDisk(DiskInterface disk);

	/**
	 * Metoda rozpoczyna działanie macierzy RAID. Przed jej użyciem nie wolno
	 * odczytywać/zapisywać danych na dyskach.
	 */
	public void startRAID();

	/**
	 * Uszkodzony dysk wymieniany jest na nowy-sprawny.
	 * 
	 * @param disk sprawny dysk zastępujący dysk uszkodzony.
	 */
	public void replaceDisk(DiskInterface disk);

	/**
	 * Zlecenie zapisu danej value do sektora sector dysku logicznego
	 * 
	 * @param sector sektor dysku logicznego, do ktorego ma nastapic zapis
	 * @param value  wartosc do zapisu
	 */
	public void write(int sector, int value);

	/**
	 * Zlecenie odczytu z sektora sector dysku logicznego
	 * 
	 * @param sector sektor dysku logicznego, ktory ma zostac odczytany
	 * @return wartosc odczytana z sektora
	 */
	public int read(int sector);

	/**
	 * Rozmiar dysku logicznego, ktory powstaje po utworzeniu z dysków "fizycznych"
	 * jednego dysku logicznego.
	 * 
	 * @return rozmiar w sektorach dysku logicznego
	 */
	public int size();

	/**
	 * Zlecenie zakonczenia operacji na dyskach. Z chwila zakocznia tej metody zadna
	 * operacja na powierzonych dyskach "fizycznych" nie moze juz zostac
	 * przeprowadzona przez dana instacje obiektu klasy implementujacej usluge. Po
	 * zakonczeniu metody shutdown wszystkie kolejne zlecenia nadchodzace od
	 * uzytkownika maja byc ignorowane i nie powodowac zmian na dysku. Oznacza to,
	 * ze zmiany stanu dyskow fizycznych moga byc realizowane wylacznie przez inne
	 * istancje klasy, ktora zaimplementuje ten interfejs.
	 * 
	 */
	public void shutdown();
}
