import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
import numpy as np
import os

def draw_box(ax, pos, size, color, label):
    """Vẽ một khối hộp 3D tại vị trí pos với kích thước size"""
    x, y, z = pos
    dx, dy, dz = size
    
    # 8 đỉnh của khối hộp
    vertices = np.array([
        [x, y, z], [x+dx, y, z], [x+dx, y+dy, z], [x, y+dy, z],
        [x, y, z+dz], [x+dx, y, z+dz], [x+dx, y+dy, z+dz], [x, y+dy, z+dz]
    ])
    
    # 6 mặt của khối hộp
    faces = [
        [vertices[0], vertices[1], vertices[2], vertices[3]],
        [vertices[4], vertices[5], vertices[6], vertices[7]], 
        [vertices[0], vertices[1], vertices[5], vertices[4]],
        [vertices[2], vertices[3], vertices[7], vertices[6]],
        [vertices[1], vertices[2], vertices[6], vertices[5]],
        [vertices[4], vertices[7], vertices[3], vertices[0]]
    ]
    
    poly3d = Poly3DCollection(faces, alpha=0.3, facecolors=color, edgecolors='k', linewidths=0.8)
    ax.add_collection3d(poly3d)
    
    # Ghi nhãn ở giữa khối
    ax.text(x+dx/2, y+dy/2, z+dz/2, label, color='black', fontsize=9, ha='center', fontweight='bold')

def run_v2_visualization():
    fig = plt.figure(figsize=(14, 10))
    ax = fig.add_subplot(111, projection='3d')

    # 1. TẦNG NETWORK (Xanh)
    draw_box(ax, [-1, -1, 3], [2, 2, 0.4], 'skyblue', "NETWORK LAYER\n(TcpServer & Handlers)")

    # 2. TẦNG CORE (Tím)
    # QueueManager Container
    draw_box(ax, [-1.2, -1.2, 1.5], [2.4, 2.4, 0.8], 'plum', "CORE: QueueManager\n(ConcurrentHashMap)")
    
    # MessageQueues bên trong
    draw_box(ax, [-0.8, -0.4, 1.6], [0.6, 0.8, 0.4], 'orchid', "MQ-1\nTopic-A")
    draw_box(ax, [0.2, -0.4, 1.6], [0.6, 0.8, 0.4], 'orchid', "MQ-2\nTopic-B")

    # 3. TẦNG STORAGE (Lục)
    draw_box(ax, [-1, -1, 0], [2, 2, 0.4], 'lightgreen', "STORAGE LAYER\n(Log & Index Files)")

    # 4. LUỒNG DỮ LIỆU
    ax.quiver(0, 0, 3, 0, 0, -0.7, color='blue', length=0.7, arrow_length_ratio=0.3)
    ax.quiver(0, 0, 1.5, 0, 0, -1, color='darkgreen', length=1, arrow_length_ratio=0.2)

    # BrokerMain bao quanh
    draw_box(ax, [-1.5, -1.5, -0.5], [3, 3, 4.5], 'grey', "")
    ax.text(-1.5, -1.5, 4, "BROKER_MAIN", color='red', fontsize=12, fontweight='bold')

    # Cấu hình hiển thị
    ax.set_xlim(-2, 2)
    ax.set_ylim(-2, 2)
    ax.set_zlim(-1, 5)
    ax.set_axis_off()
    plt.title("SHOPEE DISTRIBUTED QUEUE ARCHITECTURE V2.0", fontsize=16, pad=20)
    
    # Lưu file ảnh
    save_path = os.path.join(os.getcwd(), "architecture_v2.png")
    plt.savefig(save_path)
    print(f"Detailed 3D Visualization saved to: {save_path}")
    plt.show()

if __name__ == "__main__":
    run_v2_visualization()
