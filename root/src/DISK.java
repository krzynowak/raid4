import java.util.concurrent.atomic.AtomicIntegerArray;

public class DISK implements DiskInterface
{
    private AtomicIntegerArray data;
    private final int size = 7;


    public DISK(int mul)
    {
       System.out.println("CREATING DISK");
        this.data = new AtomicIntegerArray(this.size); 

        for(int i=0;i<this.size; i++)
        {
            this.data.set(i, (i+1) * mul);
        }
    }

    @Override
    public void write(int sector, int value) throws DiskInterface.DiskError {
       this.data.set(sector, value); 
        
    }

    @Override
    public int read(int sector) throws DiskInterface.DiskError {      
         try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return this.data.get(sector);
    }

    @Override
    public int size() {
        return this.size;
    }




    
}