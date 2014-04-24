package test.net.indiespot.struct;

import net.indiespot.struct.runtime.StructAllocationBlock;
import net.indiespot.struct.runtime.StructAllocationStack;

public class StructAllocationBlockTest {
	public static void main(String[] args) {
		int sizeof = 3 * 4;
		{
			StructAllocationBlock sab = new StructAllocationBlock(13, sizeof * 4);
			for(int i = 0; i < 4; i++) {
				System.out.println(sab.allocate(sizeof));
			}
		}
		{
			StructAllocationStack sas = new StructAllocationStack(27, sizeof * 4);

			sas.save();
			{
				System.out.println(sas.allocate(sizeof));
				System.out.println(sas.allocate(sizeof));
				sas.save();
				{
					System.out.println(sas.allocate(sizeof));
				}
				sas.restore();
				System.out.println(sas.allocate(sizeof));
			}
			sas.restore();

			sas.save();
			{
				System.out.println(sas.allocate(sizeof));
			}
			sas.restore();
		}
	}
}
