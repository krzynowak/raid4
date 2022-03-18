import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
// import java.lang.Thread;


public class RAID implements RAIDInterface
{
    private ArrayList<DiskInterface> PhysDrives;
    private RAIDState CurrentState;
    private int BrokenDriveID;
    private RestoreDrive restorer;
    ThreadPoolExecutor executor = null;
    Future futInit = null;
    Future futRest = null;

    public class Initializer implements Runnable
    {
        ArrayList<DiskInterface> Drives;
    
        public Initializer(ArrayList<DiskInterface> Drives)
        {
            this.Drives = Drives;
        }
    
        @Override
        public void run()
        {
            synchronized(this.Drives)
            {
                for(int sector = 0; sector < this.Drives.get(0).size(); sector++)
                {
                    int sum = 0;
    
                    for(int drive = 0; drive < this.Drives.size() - 1; drive++)
                    {
                        try 
                        {
                            sum += this.Drives.get(drive).read(sector);
                        } 
                        catch (DiskInterface.DiskError e) 
                        {
                            e.printStackTrace();
                        }
                    }
    
                    try 
                    {
                        this.Drives.get(this.Drives.size() - 1).write(sector, sum);
                    } 
                    catch (DiskInterface.DiskError e) 
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    protected class RestoreDrive implements Runnable
    {
        private ArrayList<DiskInterface> drives;
        private final int driveId;
        private int segCnt;
        boolean done;

        public RestoreDrive(ArrayList<DiskInterface> D, int BrokenDriveID)
        {
            this.drives = D;
            this.driveId = BrokenDriveID;
            this.done = false;
        }

        public boolean getStatus()
        {
            return this.done;
        }

        @Override
        public void run()
        {
            try
            {
                this.segCnt = this.drives.get(0).size();
                int temp[] = new int[segCnt];

                //redudant broke
                if(BrokenDriveID == (this.drives.size() - 1))
                {
                    DiskInterface disk = null;

                    //Iterate over drives one at a time
                    for(int drive = 0; drive < this.drives.size() - 1; drive++)
                    {
                        disk = this.drives.get(drive);

                        synchronized(disk)
                        {
                            for(int sector = 0; sector < this.segCnt; sector++)
                            {
                                temp[sector] += disk.read(sector);
                            }
                        }
                    }

                    disk = this.drives.get(this.driveId);

                    synchronized(disk)
                    {
                        for(int sector = 0; sector < this.segCnt; sector++)
                        {
                            disk.write(sector, temp[sector]);
                        }
                    }
                }
                else
                {
                    int tempRed[] = new int[segCnt];
                    DiskInterface disk = null;

                    //Iterate over drives one at a time - non redundant and working
                    for(int drive = 0; drive < this.drives.size() - 1; drive++)
                    {
                        if(drive != this.driveId)
                        {
                            disk = this.drives.get(drive);

                            synchronized(disk)
                            {
                                for(int sector = 0; sector < this.segCnt; sector++)
                                {
                                    temp[sector] += disk.read(sector);
                                }
                            }
                        }
                    }


                    //read redundant drive
                    disk = this.drives.get(this.drives.size() - 1);
                    synchronized(disk)
                    {
                        for(int sector = 0; sector < this.segCnt; sector++)
                        {
                            tempRed[sector] = disk.read(sector);
                        }
                    }


                    //write to broken drive
                    disk = this.drives.get(this.driveId);
                    synchronized(disk)
                    {
                        for(int sector = 0; sector < this.segCnt; sector++)
                        {
                            disk.write(sector, tempRed[sector] - temp[sector]);
                        }
                    }
                }
            }
            catch (DiskInterface.DiskError e)
            {

            }
            this.done = true;
        }
    }

    public RAID()
    {
        this.CurrentState = RAIDState.UNKNOWN;
        this.PhysDrives = new ArrayList<DiskInterface>();
        this.restorer = new RestoreDrive(null, -1);
        this.BrokenDriveID = -1;
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    private void stateCheck()
    {
        if(((this.CurrentState == RAIDState.INITIALIZATION) && this.futInit.isDone()     ) || 
           ((this.CurrentState == RAIDState.REBUILD       ) && this.restorer.getStatus() )  )
        {
            this.CurrentState = RAIDState.NORMAL;
        }
    }

    private boolean execCheck(int drive)
    {
        boolean result = false;

        if( (this.CurrentState == RAIDState.NORMAL  )                                    ||
          ( (this.CurrentState == RAIDState.DEGRADED) && (drive != this.BrokenDriveID) ) ||
          ( (this.CurrentState == RAIDState.REBUILD ) && (drive != this.BrokenDriveID) )  )
        {
            result= true;
        }

      return result;
    }

    private boolean isBroken(int drive)
    {
        boolean result = false;

        if( 
          ( (this.CurrentState == RAIDState.DEGRADED) && (drive == this.BrokenDriveID) ) ||
          ( (this.CurrentState == RAIDState.REBUILD ) && (drive == this.BrokenDriveID) )  )
        {
            result= true;
        }

      return result;
    }

    @Override
    public RAIDInterface.RAIDState getState()
    {
        this.stateCheck();

        return this.CurrentState;
    }

    @Override
    public void addDisk(DiskInterface disk)
    {
        this.PhysDrives.add(disk);
    }

    @Override
    public void startRAID()
    {
        this.CurrentState = RAIDState.INITIALIZATION;

        this.futInit = this.executor.submit(new Initializer(this.PhysDrives));
    }

    @Override
    public void replaceDisk(DiskInterface disk)
    {
        if(this.CurrentState == RAIDState.DEGRADED)
        {
            this.CurrentState = RAIDState.REBUILD;

            this.PhysDrives.set(this.BrokenDriveID, disk);
            
            this.restorer = new RestoreDrive(this.PhysDrives, this.BrokenDriveID);

            Future ft = this.executor.submit(this.restorer);
            while(!ft.isDone());
            
            this.BrokenDriveID = -1;
        }
    }

    @Override
    public void write(int sector, int value)
    {
        this.stateCheck();

        int drive = sector / this.PhysDrives.get(0).size();
        int driveSector = sector % this.PhysDrives.get(0).size();
        DiskInterface disk = null;
        DiskInterface redundant = this.PhysDrives.get(this.PhysDrives.size() - 1);

        if( execCheck(drive) )
        {
            disk = this.PhysDrives.get(drive);
            


            int a = 0;
            int b = 0;
            
            try
            {
                synchronized(disk)
                {
                    a = disk.read(driveSector);
                    disk.write(driveSector, value);
                }
            }
            catch (DiskInterface.DiskError e)
            {
                this.BrokenDriveID = drive;
                this.CurrentState = RAIDState.DEGRADED;
            }

            try
            {
                synchronized(redundant)
                {
                    b = redundant.read(driveSector);
                    redundant.write(driveSector, b + (value - a));
                }
            }
            catch (DiskInterface.DiskError e)
            {
                this.BrokenDriveID = this.PhysDrives.size() - 1;
                this.CurrentState = RAIDState.DEGRADED;
            }
        }
        else if ( isBroken(drive) )
        {
            int sum = value;

            try 
            {
                //read all non broken drives -> calculate new checksum
                for(int drv=0; drv<this.PhysDrives.size()-1;drv++)
                {
                    if(drv != drive )
                    {
                        disk = this.PhysDrives.get(drv);
                        synchronized(disk)
                        {
                            sum += disk.read(driveSector);
                        }
                    }
                }

                //update checksum
                synchronized(redundant)
                {
                    redundant.write(driveSector, sum);
                }
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int read(int sector)
    {
        this.stateCheck();

        Integer res = -1;

        int drive = sector / this.PhysDrives.get(0).size();
        int driveSector = sector % this.PhysDrives.get(0).size();

        
        if( execCheck(drive) )
        {
            DiskInterface disk = this.PhysDrives.get(drive);

            try
            {
                synchronized(disk)
                {
                    res = disk.read(driveSector);
                }
            }
            catch (DiskInterface.DiskError e)
            {

                this.BrokenDriveID = drive;
                this.CurrentState = RAIDState.DEGRADED;

                try 
                {
                    int sum = 0;

                    for(int dr = 0; dr < this.PhysDrives.size() - 1; dr++)
                    {
                        if( drive != dr )
                        {
                            synchronized(this.PhysDrives.get(dr))
                            {
                                sum += this.PhysDrives.get(dr).read(driveSector);
                            }
                        }
                    }

                    synchronized(this.PhysDrives.get(this.PhysDrives.size() - 1))
                    {
                        //non redudant drive broke -> recalc data from redundant
                        res = this.PhysDrives.get(this.PhysDrives.size() - 1).read(driveSector) - sum;
                    }
                    

                } 
                catch (DiskInterface.DiskError e1) 
                {
                    e.printStackTrace();
                }
            }
        }
        else if ( isBroken(drive) )
        {
            try 
            {
                int sum = 0;

                for(int dr = 0; dr < this.PhysDrives.size() - 1; dr++)
                {
                    if( drive != dr )
                    {
                        synchronized(this.PhysDrives.get(dr))
                        {
                            sum += this.PhysDrives.get(dr).read(driveSector);
                        }
                    }
                }

                synchronized(this.PhysDrives.get(this.PhysDrives.size() - 1))
                {
                    //non redudant drive broke -> recalc data from redundant
                    res = this.PhysDrives.get(this.PhysDrives.size() - 1).read(driveSector) - sum;
                }
            } 
            catch (DiskInterface.DiskError e) 
            {
                e.printStackTrace();
            }
        }

        return res;
    }

    @Override
    public int size()
    {   
        return (this.PhysDrives.size() - 1) * this.PhysDrives.get(0).size();
    }

    @Override
    public void shutdown()
    {
        this.CurrentState = RAIDState.UNKNOWN;
    }




    /* DEBUG SECTION */
    public void DD()
    {
        for(int i=0;i<this.PhysDrives.size();i++)
        {
            System.out.printf("AR %d ||  ", i);
            for(int j=0;j<this.PhysDrives.get(0).size();j++)
            {
                try 
                {
                    System.out.printf("%03d ||  ", this.PhysDrives.get(i).read(j));
                } 
                catch (Exception e) 
                {
                    e.printStackTrace();
                }
            }
            System.out.println();
        }
    }

    public void error(int id)
    {
        
        this.CurrentState = RAIDState.DEGRADED;
        this.BrokenDriveID = id;
        this.PhysDrives.set(this.BrokenDriveID, new DISK(0));
    }

    public static class ReadTest implements Runnable
    {
        RAID matrix;
        int sector;

        public ReadTest(RAID matrix, int sector)
        {
            this.matrix = matrix;
            this.sector = sector;
        }

        @Override
        public void run() 
        {
            System.out.printf("Sector %02d = %03d\n", sector, matrix.read(sector));
        }

    }

    public static class WriteTest implements Runnable
    {
        RAID matrix;
        int sector;
        int value;

        public WriteTest(RAID matrix, int sector, int value)
        {
            this.matrix = matrix;
            this.sector = sector;
            this.value = value;
        }

        @Override
        public void run()
        {
            matrix.write(sector, value);
        }

    }

    public static void main(String[] args)
    {
        System.out.println("Hello from main");

        DISK A = new DISK(100);
        DISK B = new DISK(10);
        DISK C = new DISK(1);
        DISK D = new DISK(19);
        
        System.out.println("Creating Array");
        RAID M = new RAID();

        System.out.printf("Pre Init state = %s\n", M.getState().name() );

        M.addDisk(A);
        M.addDisk(B);
        M.addDisk(C);
        M.addDisk(D);

        System.out.println("Drive contents");
        M.DD();

        System.out.println("Running Initialization");
        M.startRAID();

        System.out.printf("Post Init state = %s\n", M.getState().name() );

        System.out.println("Drive contents");
        M.DD();

        System.out.println("Drive contents");


        for(int i=0;i<21;i++)
        {
            WriteTest a = new WriteTest(M, i, i*3);
            Thread wr = new Thread(a);
            wr.start();

            ReadTest b = new ReadTest(M, i);
            Thread r = new Thread(b);
            r.start();

            try { Thread.sleep(20); } catch (InterruptedException e) {  e.printStackTrace(); }
        }

        try { Thread.sleep(8000); } catch (InterruptedException e) { e.printStackTrace(); }


        System.out.println("\n\n\n\n"); 
        M.error(2);

        M.DD();
        System.out.println("\n\n\n\n");
        for(int i=14;i<21;i++)
        {
            WriteTest a = new WriteTest(M, i, i);
            Thread wr = new Thread(a);
            wr.start();

            try { Thread.sleep(20); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        try { Thread.sleep(8000); } catch (InterruptedException e) { e.printStackTrace(); }

        M.DD();
    }
}